package com.xperp.ai.agent;

import com.xperp.ai.decisions.AiDecisionRepository;
import com.xperp.ai.forecasting.ForecastService;
import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.rules.ReorderRules;
import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.CountDto;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockDto;
import com.xperp.inventory.api.StockKey;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.inventory.events.InventoryCountRecorded;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.purchasing.events.SupplierPriceChanged;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Decisiones sobre existencias: discrepancias de conteo, anomalías de precio, productos estancados
 * y parámetros de reorden (AGENTS.md, 8.1 y 8.4).
 */
@Component
class InventoryAgents {

  static final double DISCREPANCY_PCT = 3.0;
  static final double PRICE_CHANGE_PCT = 10.0;
  static final double SLOW_MOVER_DAYS = 60;
  static final double SLOW_MOVER_CLEAR_DAYS = 45;
  static final int PARAMS_HISTORY_DAYS = 28;

  private final InventoryService inventory;
  private final CatalogService catalog;
  private final PurchasingService purchasing;
  private final ForecastService forecasts;
  private final AiSettingsService settings;
  private final AiDecisionRepository decisions;
  private final LlmGateway llm;
  private final ActionGateway gateway;
  private final Clock clock;

  InventoryAgents(
      InventoryService inventory,
      CatalogService catalog,
      PurchasingService purchasing,
      ForecastService forecasts,
      AiSettingsService settings,
      AiDecisionRepository decisions,
      LlmGateway llm,
      ActionGateway gateway,
      Clock clock) {
    this.inventory = inventory;
    this.catalog = catalog;
    this.purchasing = purchasing;
    this.forecasts = forecasts;
    this.settings = settings;
    this.decisions = decisions;
    this.llm = llm;
    this.gateway = gateway;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- discrepancias

  void discrepancy(InventoryCountRecorded e) {
    CountDto count = inventory.count(e.countId());
    double pct = Math.abs(count.differencePct());
    if (pct <= DISCREPANCY_PCT) {
      return;
    }
    ProductDto p = catalog.product(e.productId());
    WarehouseDto w = inventory.warehouse(e.warehouseId());
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("sku", p.sku());
    input.put("bodega", w.code());
    input.put("kardex", count.systemQty());
    input.put("fisico", count.countedQty());
    input.put("diferencia", count.difference());
    input.put("diferencia_pct", Math.round(count.differencePct() * 100) / 100.0);
    input.put("reconteo", e.recount());
    BigDecimal value = p.cost().multiply(BigDecimal.valueOf(Math.abs(count.difference())));
    String facts =
        "Conteo de %s en %s: kardex %s und, físico %s und (%+d, %s), por %s a costo."
            .formatted(
                p.name(),
                w.code(),
                Fmt.units(count.systemQty()),
                Fmt.units(count.countedQty()),
                count.difference(),
                Fmt.pct(count.differencePct()),
                Fmt.money(value));
    String ref = "count:" + count.id();

    if (e.recount()) {
      submitAlert(
          "inventory_discrepancy",
          "Reconteo de " + p.sku() + " en " + w.code(),
          ref,
          input,
          facts + " El reconteo confirma la diferencia; el ajuste sigue pendiente de aprobación.",
          "warning",
          "inventory",
          p.id());
      return;
    }

    submitAlert(
        "inventory_discrepancy",
        "Discrepancia de %s en %s".formatted(p.sku(), w.code()),
        ref,
        input,
        facts + " Supera el 3 % permitido: se avisa a gerencia.",
        "critical",
        "inventory",
        p.id());

    gateway.submit(
        ActionProposal.builder("inventory_discrepancy", "create_warehouse_task")
            .title("Reconteo de " + p.sku() + " en " + w.code())
            .trigger("inventory_count_recorded", ref)
            .input(input)
            .payload(
                Map.of(
                    "type", "count",
                    "product_id", p.id(),
                    "warehouse_id", w.id(),
                    "count_id", count.id()))
            .reasoning(facts + " Se pide un reconteo para confirmar antes de ajustar.")
            .ref("product", p.id(), p.sku())
            .build());

    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("sku", p.sku());
    vars.put("facts", facts);
    LlmGateway.Text text =
        llm.write(
            "discrepancy",
            vars,
            facts
                + " Se propone ajustar el kardex al físico; lo aprueba gerencia porque cambia el valor del inventario.");
    gateway.submit(
        ActionProposal.builder("inventory_discrepancy", "adjust_inventory")
            .title("Ajustar %s en %s (%+d und)".formatted(p.sku(), w.code(), count.difference()))
            .trigger("inventory_count_recorded", ref)
            .input(input)
            .alternatives(
                List.of(
                    Map.of("opcion", "ajustar al físico", "delta", count.difference()),
                    Map.of(
                        "opcion",
                        "esperar reconteo",
                        "nota",
                        "mantiene el kardex hasta confirmar")))
            .payload(
                Map.of(
                    "product_id", p.id(),
                    "warehouse_id", w.id(),
                    "delta", count.difference(),
                    "count_id", count.id()))
            .reasoning(text.text())
            .confidence(0.75)
            .amount(Money.round(value))
            .llm(text.trace())
            .ref("product", p.id(), p.sku())
            .build());
  }

  // ---------------------------------------------------------------- precios

  void priceChange(SupplierPriceChanged e) {
    if (Math.abs(e.changePct()) <= PRICE_CHANGE_PCT) {
      return;
    }
    ProductDto p = catalog.product(e.productId());
    SupplierDto changed = purchasing.supplier(e.supplierId());
    String facts =
        "%s cambió el costo de %s de %s a %s (%+.1f %%)."
            .formatted(
                changed.name(),
                p.name(),
                Fmt.money(e.previousCost()),
                Fmt.money(e.newCost()),
                e.changePct());
    String ref = "price:" + changed.code() + ":" + p.sku() + ":" + SimTime.today(clock);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("proveedor", changed.code());
    input.put("sku", p.sku());
    input.put("costo_anterior", Money.format(e.previousCost()));
    input.put("costo_nuevo", Money.format(e.newCost()));
    input.put("cambio_pct", e.changePct());
    submitAlert(
        "price_anomaly",
        "Alza de precio de " + p.sku() + " en " + changed.code(),
        ref,
        input,
        facts,
        "warning",
        "product",
        p.id());

    // Nueva calificación de proveedores para ese SKU en cada bodega
    List<Map<String, Object>> tables = new ArrayList<>();
    StringBuilder summary = new StringBuilder(facts);
    for (WarehouseDto w : inventory.warehouses()) {
      List<SupplierScorer.Candidate> candidates = new ArrayList<>();
      for (OfferDto o : purchasing.offersForProduct(p.id())) {
        Integer lt = o.supplier().leadTimeTo(w.id());
        if (lt != null) {
          candidates.add(
              new SupplierScorer.Candidate(
                  o.supplier().id(),
                  o.supplier().code(),
                  o.supplier().name(),
                  o.unitCost().doubleValue(),
                  lt,
                  o.supplier().onTimeRate(),
                  o.preferred()));
        }
      }
      List<SupplierScorer.Scored> scored =
          SupplierScorer.score(candidates, settings.current().weights());
      if (scored.isEmpty()) {
        continue;
      }
      for (Map<String, Object> row : ReplenishmentAgent.scoreTable(scored)) {
        row.put("bodega", w.code());
        tables.add(row);
      }
      summary
          .append(" En ")
          .append(w.code())
          .append(" la mejor oferta ahora es ")
          .append(scored.get(0).candidate().supplierName())
          .append(" (")
          .append(Fmt.score(scored.get(0).total()))
          .append(").");
    }
    gateway.submit(
        ActionProposal.builder("price_anomaly", "rescore_supplier")
            .title("Recalificar proveedores de " + p.sku())
            .trigger("supplier_price_changed", ref)
            .input(input)
            .alternatives(tables)
            .payload(Map.of("product_id", p.id(), "supplier_id", changed.id()))
            .reasoning(summary.toString())
            .ref("product", p.id(), p.sku())
            .build());
  }

  // ---------------------------------------------------------------- productos estancados

  int slowMovers(LocalDate today) {
    int count = 0;
    for (InventoryRow row : inventory.rows(null)) {
      StockDto s = row.stock();
      Double cover = s.daysOfCover();
      if (cover == null) {
        continue;
      }
      if (cover > SLOW_MOVER_DAYS) {
        if (!s.reorderBlocked() || !s.promotionSuggested()) {
          inventory.setFlags(s.productId(), s.warehouseId(), true, true);
        }
        String ref = "slow:" + row.sku() + ":" + row.warehouseCode();
        boolean recent =
            decisions.existsByDecisionTypeAndTriggerRefAndSimTimeAfter(
                "slow_mover", ref, clock.instant().minus(Duration.ofDays(7)));
        if (recent) {
          continue;
        }
        ProductDto p = catalog.product(s.productId());
        BigDecimal value = p.cost().multiply(BigDecimal.valueOf(s.onHand()));
        String reasoning =
            ("%s en %s tiene %s und, %s de cobertura con una demanda de %s und/día, y %s inmovilizados."
                    + " Se sugiere una promoción y se bloquea la recompra hasta bajar de 45 días.")
                .formatted(
                    p.name(),
                    row.warehouseCode(),
                    Fmt.units(s.onHand()),
                    Fmt.days(cover),
                    Fmt.dec(s.avgDailyDemand().doubleValue()),
                    Fmt.money(value));
        gateway.submit(
            ActionProposal.builder("slow_mover", "suggest_promotion")
                .title("Promoción sugerida para " + p.sku() + " en " + row.warehouseCode())
                .trigger("daily_cycle", ref)
                .input(
                    Map.of(
                        "sku", p.sku(),
                        "bodega", row.warehouseCode(),
                        "existencias", s.onHand(),
                        "dias_cobertura", Math.round(cover * 10) / 10.0,
                        "valor", Money.format(value)))
                .payload(Map.of("product_id", p.id(), "warehouse_id", s.warehouseId()))
                .reasoning(reasoning)
                .amount(Money.round(value))
                .ref("product", p.id(), p.sku())
                .build());
        count++;
      } else if (cover < SLOW_MOVER_CLEAR_DAYS && (s.reorderBlocked() || s.promotionSuggested())) {
        inventory.setFlags(s.productId(), s.warehouseId(), false, false);
      }
    }
    return count;
  }

  // ---------------------------------------------------------------- parámetros de reorden

  /**
   * Recalcula stock de seguridad, punto de reorden y tope (sección 8.1) con los últimos 28 días.
   * Con {@code record} queda como decisión {@code reorder_params_update} (nivel 2).
   */
  int updateParams(LocalDate today, boolean record) {
    Map<StockKey, double[]> history =
        forecasts.history(today.minusDays(PARAMS_HISTORY_DAYS), today.minusDays(1));
    List<Map<String, Object>> items = new ArrayList<>();
    int changed = 0;
    List<Map<String, Object>> changes = new ArrayList<>();
    for (InventoryRow row : inventory.rows(null)) {
      StockDto s = row.stock();
      ProductDto p = catalog.product(s.productId());
      double[] series = history.getOrDefault(s.key(), new double[0]);
      List<Integer> values = new ArrayList<>();
      for (double v : series) {
        values.add((int) Math.round(v));
      }
      ReorderRules.DemandStats stats = ReorderRules.DemandStats.of(values);
      int leadTime = leadTime(p.id(), s.warehouseId());
      Double fc = forecasts.sum(p.id(), s.warehouseId(), today, leadTime);
      double daily = fc == null ? stats.mean() : fc / leadTime;
      int ss = ReorderRules.safetyStock(ReorderRules.Z_95, stats.std(), leadTime);
      int rop = ReorderRules.reorderPoint(daily, leadTime, ss);
      int cover = ReorderRules.coverDays(p.perishable());
      int max = ReorderRules.maxStock(stats.mean());
      BigDecimal avg = BigDecimal.valueOf(stats.mean()).setScale(2, RoundingMode.HALF_UP);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("product_id", p.id());
      m.put("warehouse_id", s.warehouseId());
      m.put("sku", p.sku());
      m.put("safety_stock", ss);
      m.put("reorder_point", rop);
      m.put("max_stock", max);
      m.put("cover_days", cover);
      m.put("avg_daily_demand", avg.toPlainString());
      m.put("lead_time_days", leadTime);
      items.add(m);
      if (s.reorderPoint() == 0
          || Math.abs(rop - s.reorderPoint()) > 0.1 * Math.max(1, s.reorderPoint())) {
        changed++;
        if (changes.size() < 15) {
          changes.add(
              Map.of(
                  "sku", p.sku(),
                  "bodega", row.warehouseCode(),
                  "punto_reorden_antes", s.reorderPoint(),
                  "punto_reorden_nuevo", rop));
        }
      }
    }
    Map<String, Object> payload = Map.of("items", items);
    String reasoning =
        ("Se recalcularon los parámetros de %d existencias con los últimos %d días de demanda:"
                + " stock de seguridad = 1,65 × σ × √lead time, punto de reorden = demanda × (lead time + 1) +"
                + " stock de seguridad y tope de 30 días. %d puntos de reorden cambiaron más de 10 %%.")
            .formatted(items.size(), PARAMS_HISTORY_DAYS, changed);
    if (!record) {
      // Carga inicial: se aplica directo, sin decisión.
      for (Map<String, Object> m : items) {
        inventory.updateParams(
            ((Number) m.get("product_id")).longValue(),
            ((Number) m.get("warehouse_id")).longValue(),
            new com.xperp.inventory.api.StockParams(
                (Integer) m.get("safety_stock"),
                (Integer) m.get("reorder_point"),
                (Integer) m.get("max_stock"),
                (Integer) m.get("cover_days"),
                new BigDecimal(m.get("avg_daily_demand").toString())));
      }
      return items.size();
    }
    gateway.submit(
        ActionProposal.builder("reorder_params_update", "update_reorder_params")
            .title("Parámetros de reorden del " + SimTime.display(today))
            .trigger("simulation_day_started", "params:" + today)
            .input(
                Map.of(
                    "existencias",
                    items.size(),
                    "dias_historia",
                    PARAMS_HISTORY_DAYS,
                    "z",
                    ReorderRules.Z_95))
            .alternatives(changes)
            .payload(payload)
            .reasoning(reasoning)
            .build());
    return items.size();
  }

  /** Lead time del proveedor preferido a la bodega (o el menor de las ofertas). */
  int leadTime(long productId, long warehouseId) {
    int best = Integer.MAX_VALUE;
    int preferred = -1;
    for (OfferDto o : purchasing.offersForProduct(productId)) {
      Integer lt = o.supplier().leadTimeTo(warehouseId);
      if (lt == null) {
        continue;
      }
      best = Math.min(best, lt);
      if (o.preferred()) {
        preferred = preferred < 0 ? lt : Math.min(preferred, lt);
      }
    }
    if (preferred > 0) {
      return preferred;
    }
    return best == Integer.MAX_VALUE ? 3 : best;
  }

  private void submitAlert(
      String decisionType,
      String title,
      String ref,
      Map<String, Object> input,
      String message,
      String severity,
      String entityType,
      long entityId) {
    gateway.submit(
        ActionProposal.builder(decisionType, "raise_alert")
            .title(title)
            .trigger(decisionType, ref)
            .input(input)
            .payload(
                Map.of(
                    "type", decisionType,
                    "severity", severity,
                    "message", message,
                    "entity_type", entityType,
                    "entity_id", entityId))
            .reasoning(message)
            .ref(entityType, entityId, title)
            .build());
  }
}
