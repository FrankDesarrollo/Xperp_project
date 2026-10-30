package com.xperp.ai.agent;

import com.xperp.ai.agent.PurchaseProposals.Plan;
import com.xperp.ai.agent.PurchaseProposals.PlannedLine;
import com.xperp.ai.policies.ActionExecutor;
import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.Payloads;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.CategoryDto;
import com.xperp.finance.api.BudgetDto;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Priorización por presupuesto (AGENTS.md, 2.13 día 22 y 8.4): cuando una categoría pasa del 85 %,
 * ordena las compras por días de cobertura y margen. Lo que cabe en el presupuesto se compra; lo
 * demás queda para aprobación. El plan lo aprueba gerencia (nivel 1).
 */
@Component
class BudgetPlanner implements ActionExecutor {

  private final CatalogService catalog;
  private final LlmGateway llm;
  private final ObjectProvider<ActionGateway> gateway;
  private final ObjectProvider<ReplenishmentAgent> replenishment;
  private final Clock clock;

  BudgetPlanner(
      CatalogService catalog,
      LlmGateway llm,
      ObjectProvider<ActionGateway> gateway,
      ObjectProvider<ReplenishmentAgent> replenishment,
      Clock clock) {
    this.catalog = catalog;
    this.llm = llm;
    this.gateway = gateway;
    this.replenishment = replenishment;
    this.clock = clock;
  }

  int plan(
      long categoryId,
      BudgetDto budget,
      List<PlannedLine> lines,
      Map<PlannedLine, SupplierDto> supplierOf,
      Map<PlannedLine, InventoryRow> rowOf,
      String triggerEvent,
      String triggerRef) {
    CategoryDto category = catalog.category(categoryId);
    List<PlannedLine> ranked = new ArrayList<>(lines);
    ranked.sort(
        Comparator.comparingDouble(PlannedLine::daysOfCover)
            .thenComparing(
                (PlannedLine l) ->
                    l.unitMargin()
                        .divide(l.unitCost().max(BigDecimal.ONE), 4, RoundingMode.HALF_UP),
                Comparator.reverseOrder()));
    BigDecimal remaining = budget.available().max(BigDecimal.ZERO);
    List<PlannedLine> buy = new ArrayList<>();
    List<PlannedLine> defer = new ArrayList<>();
    BigDecimal cumulative = BigDecimal.ZERO;
    for (PlannedLine l : ranked) {
      if (cumulative.add(l.amount()).compareTo(remaining) <= 0) {
        buy.add(l);
        cumulative = cumulative.add(l.amount());
      } else {
        defer.add(l);
      }
    }
    ReplenishmentAgent agent = replenishment.getObject();
    List<Map<String, Object>> buyPlans = new ArrayList<>();
    for (Plan p : agent.consolidate(buy, supplierOf, rowOf)) {
      buyPlans.add(planPayload(p, triggerRef));
    }
    List<Map<String, Object>> deferPlans = new ArrayList<>();
    for (Plan p : agent.consolidate(defer, supplierOf, rowOf)) {
      deferPlans.add(planPayload(p, triggerRef + ":deferred"));
    }

    List<Map<String, Object>> ranking = new ArrayList<>();
    int position = 1;
    for (PlannedLine l : ranked) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("posicion", position++);
      m.put("sku", l.product().sku());
      m.put("producto", l.product().name());
      m.put(
          "dias_cobertura",
          Double.isInfinite(l.daysOfCover()) ? null : Math.round(l.daysOfCover() * 10) / 10.0);
      m.put("margen_unitario", Money.format(l.unitMargin()));
      m.put("monto", Money.format(l.amount()));
      m.put("decision", buy.contains(l) ? "comprar" : "aprobación");
      ranking.add(m);
    }
    BigDecimal planTotal =
        lines.stream().map(PlannedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    String facts =
        ("%s lleva %s del presupuesto (%s de %s) y las compras pendientes suman %s. Se priorizan %d"
                + " SKU por menor cobertura y mayor margen: %d caben en lo disponible (%s) y %d quedan"
                + " para aprobación de gerencia.")
            .formatted(
                category.name(),
                Fmt.pct(budget.usagePct()),
                Fmt.money(budget.used()),
                Fmt.money(budget.amount()),
                Fmt.money(planTotal),
                ranked.size(),
                buy.size(),
                Fmt.money(remaining),
                defer.size());
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("category", category.name());
    vars.put("facts", facts);
    LlmGateway.Text text = llm.write("budget", vars, facts);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("category_id", categoryId);
    payload.put("category_code", category.code());
    payload.put("buy", buyPlans);
    payload.put("defer", deferPlans);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("categoria", category.code());
    input.put("presupuesto", Money.format(budget.amount()));
    input.put("comprometido", Money.format(budget.committed()));
    input.put("gastado", Money.format(budget.spent()));
    input.put("uso_pct", budget.usagePct());
    input.put("compras_planeadas", Money.format(planTotal));

    String period = SimTime.period(clock.instant());
    gateway
        .getObject()
        .submit(
            ActionProposal.builder("budget_prioritization", "apply_budget_plan")
                .title(
                    "Priorizar compras de %s (presupuesto al %s)"
                        .formatted(category.code(), Fmt.pct(budget.usagePct())))
                .trigger(triggerEvent, "budget:" + categoryId + ":" + period)
                .input(input)
                .alternatives(ranking)
                .payload(payload)
                .reasoning(text.text())
                .confidence(0.85)
                .amount(Money.round(planTotal))
                .llm(text.trace())
                .build());
    return 1;
  }

  private static Map<String, Object> planPayload(Plan plan, String triggerRef) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", PurchaseProposals.title(plan));
    m.put("reasoning", PurchaseProposals.reasoning(plan, "Priorizada por presupuesto."));
    m.put("amounts_by_category", plan.amountsByCategory());
    m.put("input", PurchaseProposals.input(plan, Map.of()));
    m.put(
        "payload",
        PurchaseProposals.payload(plan, PurchaseProposals.idempotencyKey(plan, triggerRef)));
    return m;
  }

  // ---------------------------------------------------------------- ejecutor del plan

  @Override
  public String actionType() {
    return "apply_budget_plan";
  }

  @Override
  public Result execute(long decisionId, Map<String, Object> payload) {
    int bought = 0;
    int deferred = 0;
    List<ActionProposal.Ref> refs = new ArrayList<>();
    for (Map<String, Object> plan : Payloads.getList(payload, "buy")) {
      ActionGateway.Outcome o = gateway.getObject().submit(fromPlan(plan, decisionId, false));
      refs.add(
          new ActionProposal.Ref("ai_decision", o.decisionId(), "Decisión #" + o.decisionId()));
      bought++;
    }
    for (Map<String, Object> plan : Payloads.getList(payload, "defer")) {
      ActionGateway.Outcome o = gateway.getObject().submit(fromPlan(plan, decisionId, true));
      refs.add(
          new ActionProposal.Ref("ai_decision", o.decisionId(), "Decisión #" + o.decisionId()));
      deferred++;
    }
    return new Result(
        "%d OC dentro del presupuesto y %d para aprobación".formatted(bought, deferred),
        refs,
        Map.of());
  }

  private static ActionProposal fromPlan(Map<String, Object> plan, long parentId, boolean defer) {
    Map<String, Object> payload = Payloads.getMap(plan, "payload");
    ActionProposal.Builder b =
        ActionProposal.builder("replenishment", "create_purchase_order")
            .title(Payloads.getString(plan, "title"))
            .trigger("budget_prioritization", "budget-plan:" + parentId)
            .input(Payloads.getMap(plan, "input"))
            .payload(payload)
            .reasoning(Payloads.getString(plan, "reasoning"))
            .confidence(0.9)
            .amount(new BigDecimal(String.valueOf(payload.get("total"))))
            .attribute("amounts_by_category", Payloads.getMap(plan, "amounts_by_category"))
            .ref("ai_decision", parentId, "Plan #" + parentId);
    if (defer) {
      b.forceApproval("manager", "Priorización de presupuesto: no cabe en lo disponible del mes");
    }
    return b.build();
  }
}
