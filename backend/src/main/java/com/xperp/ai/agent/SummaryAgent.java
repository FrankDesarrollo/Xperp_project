package com.xperp.ai.agent;

import com.xperp.ai.decisions.AiDecision;
import com.xperp.ai.decisions.AiDecisionRepository;
import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.dashboard.api.DashboardQueries;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Resumen diario para gerencia y compras (AGENTS.md, 8.4 daily_summary). El último día del mes
 * incluye el cierre: compras, quiebres, decisiones ejecutadas, aprobadas y rechazadas.
 */
@Component
class SummaryAgent {

  private final DashboardQueries dashboard;
  private final AiDecisionRepository decisions;
  private final LlmGateway llm;
  private final ActionGateway gateway;

  SummaryAgent(
      DashboardQueries dashboard,
      AiDecisionRepository decisions,
      LlmGateway llm,
      ActionGateway gateway) {
    this.dashboard = dashboard;
    this.decisions = decisions;
    this.llm = llm;
    this.gateway = gateway;
  }

  void summarize(LocalDate day) {
    boolean monthEnd = day.getDayOfMonth() == day.lengthOfMonth();
    DashboardQueries.KpiSnapshot k = dashboard.kpis(day, day, null);
    int orders =
        (int)
            dashboard.series("orders", day, day, null).stream()
                .mapToDouble(DashboardQueries.Point::value)
                .sum();
    List<AiDecision> today =
        decisions.findBySimTimeBetween(
            SimTime.startOfDay(day), SimTime.startOfDay(day.plusDays(1)));
    Map<String, Integer> byType = new TreeMap<>();
    Map<String, Integer> byStatus = new TreeMap<>();
    for (AiDecision d : today) {
      byType.merge(d.getDecisionType(), 1, Integer::sum);
      byStatus.merge(d.getStatus(), 1, Integer::sum);
    }
    StringBuilder facts = new StringBuilder();
    facts.append(
        "Resumen del %s: ventas por %s en %d pedidos, margen bruto %s (%s), nivel de servicio %s y %s unidades perdidas."
            .formatted(
                SimTime.display(day),
                Fmt.money(k.salesPeriod()),
                orders,
                Fmt.money(k.grossMargin()),
                Fmt.pct(k.grossMarginPct()),
                Fmt.pct(k.serviceLevel() * 100),
                Fmt.units(k.lostSalesUnits())));
    facts.append(
        " Compras enviadas: %s; OC abiertas %d (%d atrasadas); %d SKU en quiebre."
            .formatted(
                Fmt.money(k.purchasesPeriod()),
                k.openPurchaseOrders(),
                k.latePurchaseOrders(),
                k.stockoutSkus()));
    facts.append(
        " La IA tomó %d decisiones: %d ejecutadas, %d esperan aprobación, %d aprobadas, %d rechazadas."
            .formatted(
                today.size(),
                byStatus.getOrDefault("executed", 0),
                byStatus.getOrDefault("pending_approval", 0),
                byStatus.getOrDefault("approved", 0),
                byStatus.getOrDefault("rejected", 0)));

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("dia", day.toString());
    input.put("ventas", k.salesPeriod().toPlainString());
    input.put("pedidos", orders);
    input.put("nivel_servicio", k.serviceLevel());
    input.put("unidades_perdidas", k.lostSalesUnits());
    input.put("compras", k.purchasesPeriod().toPlainString());
    input.put("decisiones_por_tipo", byType);
    input.put("decisiones_por_estado", byStatus);

    if (monthEnd) {
      DashboardQueries.KpiSnapshot m = dashboard.kpis(day.withDayOfMonth(1), day, null);
      BigDecimal avoided = m.overcostAssumed();
      facts.append(
          " Cierre de mes: ventas %s, compras %s, ejecución de presupuesto %s, nivel de servicio %s, %s unidades perdidas en el mes y sobrecosto asumido por reasignaciones %s. Decisiones del mes: %s ejecutadas, %s aprobadas, %s rechazadas, %s sugeridas y %s bloqueadas."
              .formatted(
                  Fmt.money(m.salesPeriod()),
                  Fmt.money(m.purchasesPeriod()),
                  Fmt.pct(m.budgetExecutionPct()),
                  Fmt.pct(m.serviceLevel() * 100),
                  Fmt.units(m.lostSalesUnits()),
                  Fmt.money(avoided),
                  Fmt.units(m.decisionsByStatus().getOrDefault("executed", 0)),
                  Fmt.units(m.decisionsByStatus().getOrDefault("approved", 0)),
                  Fmt.units(m.decisionsByStatus().getOrDefault("rejected", 0)),
                  Fmt.units(m.decisionsByStatus().getOrDefault("suggested", 0)),
                  Fmt.units(m.decisionsByStatus().getOrDefault("blocked", 0))));
      input.put(
          "mes",
          Map.of(
              "ventas", m.salesPeriod().toPlainString(),
              "compras", m.purchasesPeriod().toPlainString(),
              "presupuesto_pct", m.budgetExecutionPct(),
              "nivel_servicio", m.serviceLevel(),
              "decisiones", m.decisionsByStatus()));
    }
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("day", SimTime.display(day));
    vars.put("month_end", monthEnd);
    vars.put("facts", facts.toString());
    LlmGateway.Text text = llm.write("daily-summary", vars, facts.toString());
    gateway.submit(
        ActionProposal.builder("daily_summary", "send_daily_summary")
            .title(
                (monthEnd ? "Cierre de mes y resumen del " : "Resumen del ") + SimTime.display(day))
            .trigger("simulation_day_ended", "summary:" + day)
            .input(input)
            .payload(Map.of("day", day.toString(), "month_end", monthEnd, "text", text.text()))
            .reasoning(text.text())
            .llm(text.trace())
            .build());
  }
}
