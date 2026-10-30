package com.xperp.ai.policies;

import com.xperp.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Llamadas al LLM y gasto de la IA por día simulado (topes de las secciones 8.5 y 9.3). */
@Service
public class AiUsageService {

  private final JdbcTemplate jdbc;

  AiUsageService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Cuenta una llamada si no se pasa del límite; devuelve false si ya se alcanzó. */
  public boolean tryConsumeLlmCall(LocalDate day, int limit) {
    ensure(day);
    int updated =
        jdbc.update(
            """
            update ai_daily_usage set llm_calls = llm_calls + 1, updated_at = now()
            where company_id = ? and sim_date = ? and llm_calls < ?
            """,
            TenantContext.require(),
            Date.valueOf(day),
            limit);
    return updated == 1;
  }

  public int llmCalls(LocalDate day) {
    Integer calls =
        jdbc.query(
            "select llm_calls from ai_daily_usage where company_id = ? and sim_date = ?",
            rs -> rs.next() ? rs.getInt(1) : 0,
            TenantContext.require(),
            Date.valueOf(day));
    return calls == null ? 0 : calls;
  }

  public BigDecimal spend(LocalDate day) {
    BigDecimal spend =
        jdbc.query(
            "select spend from ai_daily_usage where company_id = ? and sim_date = ?",
            rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO,
            TenantContext.require(),
            Date.valueOf(day));
    return spend == null ? BigDecimal.ZERO : spend;
  }

  public void addSpend(LocalDate day, BigDecimal amount) {
    ensure(day);
    jdbc.update(
        """
        update ai_daily_usage set spend = spend + ?, updated_at = now()
        where company_id = ? and sim_date = ?
        """,
        amount,
        TenantContext.require(),
        Date.valueOf(day));
  }

  private void ensure(LocalDate day) {
    jdbc.update(
        "insert into ai_daily_usage (company_id, sim_date) values (?, ?) on conflict do nothing",
        TenantContext.require(),
        Date.valueOf(day));
  }
}
