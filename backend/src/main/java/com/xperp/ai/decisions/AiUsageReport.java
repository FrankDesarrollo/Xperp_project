package com.xperp.ai.decisions;

import com.xperp.ai.api.AiProperties;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.policies.PolicyEngine;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuándo y cuánto se usó la IA (ADR 0004): decisiones por tipo de disparador, cuántas resolvieron
 * las reglas y cuántas el LLM, llamadas, tokens y costo estimado. Lee solo tablas del módulo.
 */
@Service
@Transactional(readOnly = true)
public class AiUsageReport {

  private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

  public record Day(LocalDate date, int decisions, int llmCalls, long tokens) {}

  public record Report(
      LocalDate from,
      LocalDate to,
      int decisions,
      Map<String, Integer> byTrigger,
      int decidedByRules,
      int decidedWithLlm,
      int llmCalls,
      int llmLimitPerDay,
      long tokensIn,
      long tokensOut,
      BigDecimal estimatedCostUsd,
      int assistantQuestions,
      int autonomousFinancialActions,
      List<Day> byDay) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final AiSettingsService settings;
  private final AiProperties properties;

  AiUsageReport(
      NamedParameterJdbcTemplate jdbc, AiSettingsService settings, AiProperties properties) {
    this.jdbc = jdbc;
    this.settings = settings;
    this.properties = properties;
  }

  public Report report(LocalDate from, LocalDate to) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("c", TenantContext.require())
            .addValue("from", Timestamp.from(SimTime.startOfDay(from)))
            .addValue("to", Timestamp.from(SimTime.startOfDay(to.plusDays(1))))
            .addValue("fromDate", Date.valueOf(from))
            .addValue("toDate", Date.valueOf(to))
            .addValue("zone", SimTime.ZONE.getId())
            .addValue("financial", List.copyOf(PolicyEngine.HUMAN_APPROVAL_ACTIONS));

    Map<String, Integer> byTrigger = new LinkedHashMap<>();
    for (String kind :
        List.of(
            TriggerKind.EVENT, TriggerKind.CONDITION, TriggerKind.REQUEST, TriggerKind.SCHEDULE)) {
      byTrigger.put(kind, 0);
    }
    int[] totals = new int[3]; // decisiones, por reglas, con LLM
    long[] tokens = new long[2];
    jdbc.query(
        """
        select trigger_event, llm_used, count(*) as n,
               coalesce(sum(tokens_in), 0) as tin, coalesce(sum(tokens_out), 0) as tout
        from ai_decisions
        where company_id = :c and sim_time >= :from and sim_time < :to
        group by trigger_event, llm_used
        """,
        p,
        rs -> {
          int n = rs.getInt("n");
          byTrigger.merge(TriggerKind.of(rs.getString("trigger_event")), n, Integer::sum);
          totals[0] += n;
          totals[rs.getBoolean("llm_used") ? 2 : 1] += n;
          tokens[0] += rs.getLong("tin");
          tokens[1] += rs.getLong("tout");
        });

    Map<LocalDate, int[]> days = new TreeMap<>();
    Map<LocalDate, Long> dayTokens = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      days.put(d, new int[2]);
      dayTokens.put(d, 0L);
    }
    jdbc.query(
        """
        select (sim_time at time zone :zone)::date as day, count(*) as n,
               coalesce(sum(coalesce(tokens_in, 0) + coalesce(tokens_out, 0)), 0) as t
        from ai_decisions
        where company_id = :c and sim_time >= :from and sim_time < :to
        group by 1
        """,
        p,
        rs -> {
          LocalDate day = rs.getDate("day").toLocalDate();
          days.computeIfAbsent(day, k -> new int[2])[0] = rs.getInt("n");
          dayTokens.put(day, rs.getLong("t"));
        });
    int[] llmCalls = new int[1];
    jdbc.query(
        """
        select sim_date, llm_calls from ai_daily_usage
        where company_id = :c and sim_date between :fromDate and :toDate
        """,
        p,
        rs -> {
          LocalDate day = rs.getDate("sim_date").toLocalDate();
          days.computeIfAbsent(day, k -> new int[2])[1] = rs.getInt("llm_calls");
          llmCalls[0] += rs.getInt("llm_calls");
        });
    List<Day> byDay = new ArrayList<>();
    days.forEach((day, v) -> byDay.add(new Day(day, v[0], v[1], dayTokens.getOrDefault(day, 0L))));

    Integer questions =
        jdbc.queryForObject(
            """
            select count(*) from assistant_messages
            where company_id = :c and role = 'user' and created_at >= :from and created_at < :to
            """,
            p,
            Integer.class);
    Integer autonomous =
        jdbc.queryForObject(
            """
            select count(*) from ai_decisions
            where company_id = :c and sim_time >= :from and sim_time < :to
              and action_type in (:financial) and policy_result = 'executed'
              and approved_in_decision_id is null
            """,
            p,
            Integer.class);

    return new Report(
        from,
        to,
        totals[0],
        byTrigger,
        totals[1],
        totals[2],
        llmCalls[0],
        settings.current().llmMaxCallsPerDay(),
        tokens[0],
        tokens[1],
        cost(tokens[0], tokens[1]),
        questions == null ? 0 : questions,
        autonomous == null ? 0 : autonomous,
        byDay);
  }

  /** Costo estimado en USD; null si no se configuró el precio del proveedor. */
  BigDecimal cost(long tokensIn, long tokensOut) {
    BigDecimal in = properties.llmPriceInputPerMtokUsd();
    BigDecimal out = properties.llmPriceOutputPerMtokUsd();
    if (in == null || out == null) {
      return null;
    }
    return in.multiply(BigDecimal.valueOf(tokensIn))
        .add(out.multiply(BigDecimal.valueOf(tokensOut)))
        .divide(MILLION, 4, RoundingMode.HALF_UP);
  }
}
