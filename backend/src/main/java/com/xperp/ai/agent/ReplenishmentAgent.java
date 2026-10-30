package com.xperp.ai.agent;

import com.xperp.ai.agent.PurchaseProposals.Plan;
import com.xperp.ai.agent.PurchaseProposals.PlannedLine;
import com.xperp.ai.decisions.AiDecisionRepository;
import com.xperp.ai.forecasting.ForecastService;
import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.rules.ReorderRules;
import com.xperp.ai.rules.StockAdvice;
import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockKey;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.OpenLine;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reabastecimiento (AGENTS.md, 4.2 y 8.4): reglas → pronóstico → puntaje → agente → políticas.
 *
 * <ol>
 *   <li>Si {@code disponible + en_tránsito ≤ punto_de_reorden}, calcula la cantidad sugerida.
 *   <li>Si la otra bodega tiene más de 30 días de cobertura y esta está en quiebre o riesgo,
 *       propone traslado en vez de comprar.
 *   <li>Califica las ofertas; con ganador claro decide por reglas, con empate decide el LLM.
 *   <li>Consolida por proveedor y bodega, respeta el pedido mínimo y revisa el presupuesto.
 * </ol>
 */
@Component
class ReplenishmentAgent {

  private static final Logger log = LoggerFactory.getLogger(ReplenishmentAgent.class);
  static final double BUDGET_THRESHOLD = 85.0;

  private final InventoryService inventory;
  private final CatalogService catalog;
  private final PurchasingService purchasing;
  private final FinanceService finance;
  private final ForecastService forecasts;
  private final AiSettingsService settings;
  private final LlmGateway llm;
  private final ActionGateway gateway;
  private final AiDecisionRepository decisions;
  private final TransferAgent transfers;
  private final BudgetPlanner budgetPlanner;
  private final Clock clock;

  ReplenishmentAgent(
      InventoryService inventory,
      CatalogService catalog,
      PurchasingService purchasing,
      FinanceService finance,
      ForecastService forecasts,
      AiSettingsService settings,
      LlmGateway llm,
      ActionGateway gateway,
      AiDecisionRepository decisions,
      TransferAgent transfers,
      BudgetPlanner budgetPlanner,
      Clock clock) {
    this.inventory = inventory;
    this.catalog = catalog;
    this.purchasing = purchasing;
    this.finance = finance;
    this.forecasts = forecasts;
    this.settings = settings;
    this.llm = llm;
    this.gateway = gateway;
    this.decisions = decisions;
    this.transfers = transfers;
    this.budgetPlanner = budgetPlanner;
    this.clock = clock;
  }

  /** Ciclo diario de las 06:00 sobre todas las existencias. */
  int runDaily(LocalDate today) {
    return run(today, null, "daily_cycle", "daily:" + today);
  }

  /** Revisión urgente de una existencia que bajó del stock de seguridad. */
  int runUrgent(StockKey key) {
    String hour = SimTime.display(clock.instant()).replace(" ", "T");
    return run(
        SimTime.today(clock),
        Set.of(key),
        "stock_below_safety",
        "urgent:" + key.productId() + ":" + key.warehouseId() + ":" + hour);
  }

  private int run(LocalDate today, Set<StockKey> only, String triggerEvent, String triggerRef) {
    SupplierScorer.Weights weights = settings.current().weights();
    List<InventoryRow> rows = inventory.rows(null);
    Map<StockKey, InventoryRow> byKey = new HashMap<>();
    rows.forEach(r -> byKey.put(r.stock().key(), r));
    Set<StockKey> withOpenPo = new HashSet<>();
    for (OpenLine l : purchasing.openLines()) {
      withOpenPo.add(new StockKey(l.productId(), l.warehouseId()));
    }

    List<PlannedLine> planned = new ArrayList<>();
    Map<PlannedLine, SupplierDto> supplierOf = new LinkedHashMap<>();
    Map<PlannedLine, InventoryRow> rowOf = new HashMap<>();
    int transfersProposed = 0;

    for (InventoryRow row : rows) {
      StockKey key = row.stock().key();
      if (only != null && !only.contains(key)) {
        continue;
      }
      ProductDto product = catalog.product(key.productId());
      if (!product.active() || row.stock().reorderBlocked() || withOpenPo.contains(key)) {
        continue;
      }
      int available = row.stock().available();
      if (!ReorderRules.needsReorder(available, row.inTransit(), row.stock().reorderPoint())) {
        continue;
      }
      if (hasPending("repl:" + product.sku() + ":" + row.warehouseCode())
          || hasPending("transfer:" + product.sku() + ":" + row.warehouseCode())) {
        continue;
      }
      double daily = row.stock().avgDailyDemand().doubleValue();
      double cover = ReorderRules.daysOfCover(available, daily);

      // Traslado antes que compra: quiebre o riesgo aquí y más de 30 días en la otra bodega.
      if (StockAdvice.urgent(available, cover)) {
        InventoryRow donor = TransferAgent.donor(rows, row);
        if (donor != null && row.inTransit() == 0) {
          if (transfers.propose(row, donor, today, triggerEvent)) {
            transfersProposed++;
            continue;
          }
        }
      }

      List<OfferDto> offers =
          purchasing.offersForProduct(product.id()).stream()
              .filter(o -> o.supplier().leadTimeTo(key.warehouseId()) != null)
              .toList();
      if (offers.isEmpty()) {
        log.warn("Sin ofertas para {} en {}", product.sku(), row.warehouseCode());
        continue;
      }
      List<SupplierScorer.Candidate> candidates = new ArrayList<>();
      Map<Long, OfferDto> offerBySupplier = new HashMap<>();
      for (OfferDto o : offers) {
        offerBySupplier.put(o.supplier().id(), o);
        candidates.add(
            new SupplierScorer.Candidate(
                o.supplier().id(),
                o.supplier().code(),
                o.supplier().name(),
                o.unitCost().doubleValue(),
                o.supplier().leadTimeTo(key.warehouseId()),
                o.supplier().onTimeRate(),
                o.preferred()));
      }
      List<SupplierScorer.Scored> scored = SupplierScorer.score(candidates, weights);
      int winner = 0;
      String reason = clearWinnerReason(scored);
      ActionProposal.LlmTrace trace = ActionProposal.LlmTrace.NONE;
      if (SupplierScorer.isTie(scored)) {
        LlmGateway.Choice choice = tieBreak(product, row, scored, reason);
        winner = choice.index();
        reason = choice.reasoning();
        trace = choice.trace();
      }
      SupplierScorer.Scored best = scored.get(winner);
      OfferDto offer = offerBySupplier.get(best.candidate().supplierId());
      int leadTime = best.candidate().leadTimeDays();
      int horizon = leadTime + row.stock().coverDays();
      Double fc = forecasts.sum(product.id(), key.warehouseId(), today, horizon);
      double forecast = fc == null ? daily * horizon : fc;
      int qty =
          ReorderRules.orderQuantity(
              forecast,
              row.stock().safetyStock(),
              available,
              row.inTransit(),
              product.unitsPerCase(),
              offer.minOrderCases(),
              row.stock().maxStock());
      if (qty <= 0) {
        continue;
      }
      PlannedLine line =
          new PlannedLine(
              product,
              qty,
              offer.unitCost(),
              available,
              row.inTransit(),
              row.stock().reorderPoint(),
              row.stock().safetyStock(),
              daily,
              forecast,
              horizon,
              cover,
              scoreTable(scored),
              reason,
              trace);
      planned.add(line);
      supplierOf.put(line, offer.supplier());
      rowOf.put(line, row);
    }

    // Presupuesto: si una categoría pasa del 85 %, el plan de esa categoría va a priorización.
    String period = SimTime.period(clock.instant());
    Map<Long, List<PlannedLine>> byCategory = new LinkedHashMap<>();
    planned.forEach(
        l -> byCategory.computeIfAbsent(l.product().categoryId(), k -> new ArrayList<>()).add(l));
    List<PlannedLine> normal = new ArrayList<>();
    int submitted = transfersProposed;
    for (Map.Entry<Long, List<PlannedLine>> e : byCategory.entrySet()) {
      BudgetDto budget = finance.budget(e.getKey(), period);
      BigDecimal planTotal =
          e.getValue().stream().map(PlannedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
      double projected =
          budget.amount().signum() == 0
              ? 0
              : budget
                  .used()
                  .add(planTotal)
                  .multiply(BigDecimal.valueOf(100))
                  .divide(budget.amount(), 2, java.math.RoundingMode.HALF_UP)
                  .doubleValue();
      if (budget.amount().signum() > 0 && projected > BUDGET_THRESHOLD) {
        if (!hasPending("budget:" + e.getKey() + ":" + period)) {
          submitted +=
              budgetPlanner.plan(
                  e.getKey(), budget, e.getValue(), supplierOf, rowOf, triggerEvent, triggerRef);
        }
      } else {
        normal.addAll(e.getValue());
      }
    }

    for (Plan plan : consolidate(normal, supplierOf, rowOf)) {
      submit(plan, triggerEvent, triggerRef, null);
      submitted++;
    }
    return submitted;
  }

  /** Agrupa por proveedor y bodega en una sola OC y respeta el pedido mínimo. */
  List<Plan> consolidate(
      List<PlannedLine> lines,
      Map<PlannedLine, SupplierDto> supplierOf,
      Map<PlannedLine, InventoryRow> rowOf) {
    Map<String, List<PlannedLine>> groups = new LinkedHashMap<>();
    Map<String, SupplierDto> suppliers = new HashMap<>();
    Map<String, InventoryRow> rows = new HashMap<>();
    for (PlannedLine l : lines) {
      SupplierDto s = supplierOf.get(l);
      InventoryRow row = rowOf.get(l);
      String key = s.id() + ":" + row.stock().warehouseId();
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
      suppliers.put(key, s);
      rows.put(key, row);
    }
    List<Plan> plans = new ArrayList<>();
    for (Map.Entry<String, List<PlannedLine>> e : groups.entrySet()) {
      SupplierDto s = suppliers.get(e.getKey());
      InventoryRow row = rows.get(e.getKey());
      List<PlannedLine> group = meetMinimum(s, new ArrayList<>(e.getValue()));
      plans.add(new Plan(s, row.stock().warehouseId(), row.warehouseCode(), group));
    }
    return plans;
  }

  /** Si la OC no llega al pedido mínimo, sube cajas del SKU de más rotación hasta alcanzarlo. */
  static List<PlannedLine> meetMinimum(SupplierDto s, List<PlannedLine> group) {
    BigDecimal total =
        group.stream().map(PlannedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.compareTo(s.minOrderAmount()) >= 0) {
      return group;
    }
    PlannedLine fastest = group.get(0);
    for (PlannedLine l : group) {
      if (l.dailyDemand() > fastest.dailyDemand()) {
        fastest = l;
      }
    }
    BigDecimal caseCost =
        fastest.unitCost().multiply(BigDecimal.valueOf(fastest.product().unitsPerCase()));
    if (caseCost.signum() <= 0) {
      return group;
    }
    BigDecimal missing = s.minOrderAmount().subtract(total);
    int extraCases = missing.divide(caseCost, 0, java.math.RoundingMode.CEILING).intValue();
    int extraUnits = extraCases * fastest.product().unitsPerCase();
    // No se infla más del doble de lo planeado: prefiere quedarse corto del mínimo.
    extraUnits = Math.min(extraUnits, fastest.quantity());
    PlannedLine bumped =
        new PlannedLine(
            fastest.product(),
            fastest.quantity() + extraUnits,
            fastest.unitCost(),
            fastest.available(),
            fastest.inTransit(),
            fastest.reorderPoint(),
            fastest.safetyStock(),
            fastest.dailyDemand(),
            fastest.forecastHorizon(),
            fastest.horizonDays(),
            fastest.daysOfCover(),
            fastest.scores(),
            fastest.winnerReason()
                + " Se agregaron "
                + extraUnits
                + " und para acercarse al pedido mínimo de "
                + Fmt.money(s.minOrderAmount())
                + ".",
            fastest.llm());
    List<PlannedLine> result = new ArrayList<>(group);
    result.set(group.indexOf(fastest), bumped);
    return result;
  }

  /** Envía la propuesta de una OC al ActionGateway. */
  ActionGateway.Outcome submit(
      Plan plan, String triggerEvent, String triggerRef, ActionProposal.ForcedApproval forced) {
    ActionProposal.LlmTrace trace =
        plan.lines().stream()
            .map(PlannedLine::llm)
            .filter(ActionProposal.LlmTrace::used)
            .findFirst()
            .orElse(ActionProposal.LlmTrace.NONE);
    String prefix =
        "urgent".equals(triggerRef.split(":")[0])
            ? "Revisión urgente: el disponible bajó del stock de seguridad."
            : null;
    ActionProposal.Builder b =
        PurchaseProposals.builder(plan, "replenishment", triggerEvent, triggerRef)
            .input(PurchaseProposals.input(plan, Map.of("disparador", triggerRef)))
            .reasoning(PurchaseProposals.reasoning(plan, prefix))
            .confidence(trace.used() ? 0.8 : 0.95)
            .llm(trace);
    if (forced != null) {
      b.forceApproval(forced.role(), forced.reason());
    }
    return gateway.submit(b.build());
  }

  private LlmGateway.Choice tieBreak(
      ProductDto product, InventoryRow row, List<SupplierScorer.Scored> scored, String fallback) {
    List<LlmGateway.Option> options = new ArrayList<>();
    for (SupplierScorer.Scored s : scored) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("costo_unitario", s.candidate().unitCost());
      data.put("entrega_dias", s.candidate().leadTimeDays());
      data.put("cumplimiento", s.candidate().fulfillment());
      data.put("preferido", s.candidate().preferred());
      data.put("puntaje", s.total());
      options.add(
          new LlmGateway.Option(s.candidate().supplierCode(), s.candidate().supplierName(), data));
    }
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("sku", product.sku());
    vars.put("product", product.name());
    vars.put("warehouse", row.warehouseCode());
    vars.put("available", row.stock().available());
    vars.put("daily_demand", row.stock().avgDailyDemand());
    vars.put(
        "days_of_cover",
        row.stock().daysOfCover() == null ? "sin demanda" : Fmt.dec(row.stock().daysOfCover()));
    String reasoning =
        "Empate técnico entre %s (%s) y %s (%s): se elige %s por mayor puntaje."
            .formatted(
                scored.get(0).candidate().supplierName(),
                Fmt.score(scored.get(0).total()),
                scored.get(1).candidate().supplierName(),
                Fmt.score(scored.get(1).total()),
                scored.get(0).candidate().supplierName());
    return llm.choose("replenishment", vars, options, 0, reasoning);
  }

  private static String clearWinnerReason(List<SupplierScorer.Scored> scored) {
    SupplierScorer.Scored best = scored.get(0);
    StringBuilder sb = new StringBuilder();
    sb.append(best.candidate().supplierName())
        .append(" ganó con ")
        .append(Fmt.score(best.total()))
        .append(" puntos");
    if (scored.size() > 1) {
      sb.append(" frente a ")
          .append(Fmt.score(scored.get(1).total()))
          .append(" de ")
          .append(scored.get(1).candidate().supplierName());
    }
    sb.append(" (costo ")
        .append(Fmt.money(BigDecimal.valueOf(best.candidate().unitCost())))
        .append(", entrega en ")
        .append(best.candidate().leadTimeDays())
        .append(
            best.candidate().leadTimeDays() == 1 ? " día, cumplimiento " : " días, cumplimiento ")
        .append(Fmt.pct(best.candidate().fulfillment() * 100))
        .append(").");
    return sb.toString();
  }

  static List<Map<String, Object>> scoreTable(List<SupplierScorer.Scored> scored) {
    List<Map<String, Object>> table = new ArrayList<>();
    for (SupplierScorer.Scored s : scored) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("proveedor", s.candidate().supplierCode());
      m.put("nombre", s.candidate().supplierName());
      m.put("costo_unitario", s.candidate().unitCost());
      m.put("entrega_dias", s.candidate().leadTimeDays());
      m.put("cumplimiento", s.candidate().fulfillment());
      m.put("preferido", s.candidate().preferred());
      m.put("puntaje_precio", s.priceScore());
      m.put("puntaje_tiempo", s.leadTimeScore());
      m.put("puntaje", s.total());
      table.add(m);
    }
    return table;
  }

  private boolean hasPending(String triggerRef) {
    return decisions.existsByTriggerRefAndStatusIn(
        triggerRef, List.of("pending_approval", "executing"));
  }
}
