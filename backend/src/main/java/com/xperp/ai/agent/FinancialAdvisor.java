package com.xperp.ai.agent;

import com.xperp.ai.decisions.AiDecisionRepository;
import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.rules.FinanceRules;
import com.xperp.ai.rules.FinanceRules.Finding;
import com.xperp.dashboard.api.DashboardQueries;
import com.xperp.dashboard.api.DashboardQueries.FinanceSnapshot;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Revisión financiera (ADR 0004): ingresos, gastos, presupuesto, cuentas por pagar y capital en
 * inventario. Las reglas calculan las cifras y los hallazgos; el LLM, si está, solo redacta el
 * resumen. Corre cuando alguien la pide, en la revisión programada (cada lunes y al cierre del mes)
 * o cuando aparece una condición grave; no hay una IA mirando las finanzas todo el tiempo. No
 * ejecuta nada: las recomendaciones las decide una persona.
 */
@Component
public class FinancialAdvisor {

  public static final String DECISION_TYPE = "financial_review";

  /** Una condición grave ya avisada no se vuelve a avisar en esta ventana. */
  static final Duration CONDITION_COOLDOWN = Duration.ofDays(7);

  private final DashboardQueries dashboard;
  private final LlmGateway llm;
  private final ActionGateway gateway;
  private final AiDecisionRepository decisions;
  private final Clock clock;

  FinancialAdvisor(
      DashboardQueries dashboard,
      LlmGateway llm,
      ActionGateway gateway,
      AiDecisionRepository decisions,
      Clock clock) {
    this.dashboard = dashboard;
    this.llm = llm;
    this.gateway = gateway;
    this.decisions = decisions;
    this.clock = clock;
  }

  /** Cifras del mes de {@code day}, desde el primer día hasta {@code day}. */
  public FinanceSnapshot snapshot(LocalDate day) {
    return dashboard.finance(day.withDayOfMonth(1), day);
  }

  /** Corre una revisión del mes en curso, hoy, y la deja en la bitácora. Devuelve su id. */
  public long review(String trigger) {
    return review(trigger, SimTime.today(clock));
  }

  /** Corre una revisión del mes de {@code day} (el cierre se calcula con el último día). */
  public long review(String trigger, LocalDate day) {
    return review(trigger, "finance:" + trigger + ":" + day, snapshot(day));
  }

  /**
   * Revisión por condición: las reglas, sin LLM, miran si hay un hallazgo grave que no se haya
   * avisado en los últimos 7 días. Solo entonces se corre la revisión completa.
   */
  public boolean checkConditions(LocalDate day) {
    FinanceSnapshot s = snapshot(day);
    for (Finding f : FinanceRules.findings(s)) {
      if (!FinanceRules.HIGH.equals(f.severity())) {
        continue;
      }
      String ref = "finance-alert:" + f.key();
      boolean recent =
          decisions.existsByDecisionTypeAndTriggerRefAndSimTimeAfter(
              DECISION_TYPE, ref, clock.instant().minus(CONDITION_COOLDOWN));
      if (!recent) {
        review("financial_condition", ref, s);
        return true;
      }
    }
    return false;
  }

  private long review(String trigger, String ref, FinanceSnapshot s) {
    List<Finding> findings = FinanceRules.findings(s);
    String facts = FinanceRules.facts(s, findings);
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("from", SimTime.display(s.from()));
    vars.put("to", SimTime.display(s.to()));
    vars.put("facts", facts);
    LlmGateway.Text text = llm.write("financial-review", vars, facts);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("period_from", s.from().toString());
    payload.put("period_to", s.to().toString());
    payload.put("summary", text.text());
    payload.put("findings", findings.stream().map(Finding::toMap).toList());
    long high = findings.stream().filter(f -> FinanceRules.HIGH.equals(f.severity())).count();
    return gateway
        .submit(
            ActionProposal.builder(DECISION_TYPE, "send_financial_review")
                .title(title(trigger, findings.size(), high))
                .trigger(trigger, ref)
                .input(input(s))
                .payload(payload)
                .reasoning(text.text())
                .llm(text.trace())
                .build())
        .decisionId();
  }

  static String title(String trigger, int findings, long high) {
    String what =
        switch (trigger) {
          case "user_request" -> "Análisis financiero a pedido";
          case "financial_review_weekly" -> "Revisión financiera semanal";
          case "month_close" -> "Cierre financiero del mes";
          case "financial_condition" -> "Alerta financiera";
          default -> "Revisión financiera";
        };
    if (findings == 0) {
      return what + ": sin hallazgos";
    }
    return "%s: %d %s%s"
        .formatted(
            what,
            findings,
            findings == 1 ? "hallazgo" : "hallazgos",
            high > 0 ? " (" + high + (high == 1 ? " grave)" : " graves)") : "");
  }

  private static Map<String, Object> input(FinanceSnapshot s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("desde", s.from().toString());
    m.put("hasta", s.to().toString());
    m.put("ventas", Money.format(s.revenue()));
    m.put("costo_ventas", Money.format(s.costOfSales()));
    m.put("margen_bruto", Money.format(s.grossMargin()));
    m.put("margen_pct", s.grossMarginPct());
    m.put("fletes", Money.format(s.freight()));
    m.put("compras_recibidas", Money.format(s.purchasesReceived()));
    m.put("compras_comprometidas", Money.format(s.purchasesCommitted()));
    m.put("ventas_perdidas", Money.format(s.lostSalesAmount()));
    m.put("por_pagar", Money.format(s.payablesOpen()));
    m.put("por_pagar_7_dias", Money.format(s.payablesDue7d()));
    m.put("por_pagar_vencido", Money.format(s.payablesOverdue()));
    m.put("inventario_costo", Money.format(s.inventoryValue()));
    m.put("inventario_exceso", Money.format(s.inventoryExcessValue()));
    m.put("inventario_estancado", Money.format(s.slowMoverValue()));
    m.put(
        "presupuesto",
        s.budgets().stream()
            .map(
                b ->
                    Map.<String, Object>of(
                        "categoria", b.categoryCode(),
                        "uso_pct", b.usedPct(),
                        "proyectado_pct", b.projectedPct()))
            .toList());
    return m;
  }
}
