package com.xperp.ai.agent;

import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.ai.tools.WriteTools;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockDto;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.purchasing.events.PurchaseOrderStatusChanged;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Excepciones de OC (AGENTS.md, 4.2 paso 9 y 8.4 po_exception): ante un rechazo o un retraso, la IA
 * evalúa reasignar, esperar o cancelar. Decide el LLM (con el mock, la opción de reglas). El nivel
 * depende del sobrecosto: ≤ 10 % se ejecuta; más, lo aprueba Compras.
 */
@Component
class ExceptionAgent {

  private static final Logger log = LoggerFactory.getLogger(ExceptionAgent.class);

  private record Alternative(SupplierDto supplier, BigDecimal total, int leadTime, double score) {}

  private final PurchasingService purchasing;
  private final InventoryService inventory;
  private final CatalogService catalog;
  private final AiSettingsService settings;
  private final LlmGateway llm;
  private final WriteTools tools;
  private final Clock clock;

  ExceptionAgent(
      PurchasingService purchasing,
      InventoryService inventory,
      CatalogService catalog,
      AiSettingsService settings,
      LlmGateway llm,
      WriteTools tools,
      Clock clock) {
    this.purchasing = purchasing;
    this.inventory = inventory;
    this.catalog = catalog;
    this.settings = settings;
    this.llm = llm;
    this.tools = tools;
    this.clock = clock;
  }

  void handle(PurchaseOrderStatusChanged event) {
    boolean rejected = "rejected".equals(event.status());
    boolean delayed = "delayed".equals(event.status());
    if (!rejected && !delayed || event.status().equals(event.previousStatus())) {
      return;
    }
    PurchaseOrderDto po = purchasing.order(event.purchaseOrderId());
    if (!po.status().equals(event.status())) {
      return; // ya cambió (otra decisión la atendió)
    }
    WarehouseDto warehouse = inventory.warehouse(po.warehouseId());
    Instant now = Instant.now(clock);

    // Cobertura actual de lo pendiente
    double minCover = Double.POSITIVE_INFINITY;
    String criticalSku = null;
    List<Map<String, Object>> lines = new ArrayList<>();
    for (PurchaseOrderDto.Line l : po.lines()) {
      if (l.pending() <= 0) {
        continue;
      }
      StockDto stock = inventory.stock(l.productId(), po.warehouseId());
      ProductDto p = catalog.product(l.productId());
      double daily = stock.avgDailyDemand().doubleValue();
      double cover = daily <= 0 ? Double.POSITIVE_INFINITY : Math.max(0, stock.available()) / daily;
      if (cover < minCover) {
        minCover = cover;
        criticalSku = p.sku();
      }
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("sku", p.sku());
      m.put("pendiente", l.pending());
      m.put("disponible", stock.available());
      m.put("dias_cobertura", Double.isInfinite(cover) ? null : Math.round(cover * 10) / 10.0);
      lines.add(m);
    }
    double daysUntilNew =
        po.expectedAt() == null
            ? 0
            : Math.max(0, Duration.between(now, po.expectedAt()).toHours() / 24.0);

    // Alternativas: proveedores que venden todo lo pendiente y despachan a la bodega
    // Urgente si el inventario no aguanta: rechazos con menos de 3 días de cobertura o retrasos
    // que llegan después de que se acabe. Si no, pesa el precio y el proveedor preferido.
    boolean urgent = rejected ? minCover < 3 : minCover < daysUntilNew + 0.5;
    List<Alternative> alternatives = alternatives(po, urgent);
    BigDecimal oldTotal = pendingTotal(po);

    List<LlmGateway.Option> options = new ArrayList<>();
    List<Map<String, Object>> altTable = new ArrayList<>();
    for (Alternative a : alternatives) {
      double overcost = overcostPct(oldTotal, a.total());
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("accion", "reasignar");
      data.put("proveedor", a.supplier().code());
      data.put("total", Money.format(a.total()));
      data.put("entrega_dias", a.leadTime());
      data.put("cumplimiento", a.supplier().onTimeRate());
      data.put("sobrecosto_pct", overcost);
      data.put("puntaje", a.score());
      options.add(
          new LlmGateway.Option(
              "reassign:" + a.supplier().code(), "Reasignar a " + a.supplier().name(), data));
      altTable.add(data);
    }
    if (delayed) {
      Map<String, Object> wait = new LinkedHashMap<>();
      wait.put("accion", "esperar");
      wait.put("nueva_fecha", SimTime.display(po.expectedAt()));
      wait.put("dias_hasta_entrega", Math.round(daysUntilNew * 10) / 10.0);
      options.add(new LlmGateway.Option("wait", "Esperar la nueva fecha", wait));
      altTable.add(wait);
    }
    Map<String, Object> cancel = new LinkedHashMap<>();
    cancel.put("accion", "cancelar");
    options.add(new LlmGateway.Option("cancel", "Cancelar la OC", cancel));
    altTable.add(cancel);

    // Regla por defecto
    int fallback;
    String reasoning;
    boolean stockLasts = minCover >= daysUntilNew + 0.5;
    if (delayed && (stockLasts || alternatives.isEmpty())) {
      fallback = options.size() - 2; // esperar
      reasoning =
          "%s se retrasa hasta %s. El SKU más justo (%s) cubre %s, suficiente para esperar sin quiebre."
              .formatted(
                  po.number(), SimTime.display(po.expectedAt()), criticalSku, Fmt.days(minCover));
    } else if (!alternatives.isEmpty()) {
      fallback = 0;
      Alternative best = alternatives.get(0);
      double overcost = overcostPct(oldTotal, best.total());
      reasoning =
          (rejected
                      ? "%s rechazó %s (%s). "
                      : "%s retrasa %s hasta %s y el inventario de %s solo cubre %s. ")
                  .formatted(
                      po.supplierName(),
                      po.number(),
                      rejected
                          ? (event.reason() == null ? "sin motivo" : event.reason())
                          : SimTime.display(po.expectedAt()),
                      criticalSku,
                      Fmt.days(minCover))
              + "Se reasigna a %s, que entrega en %s por %s; el sobrecosto es de %s (%s) frente a %s."
                  .formatted(
                      best.supplier().name(),
                      best.leadTime() == 1 ? "1 día" : best.leadTime() + " días",
                      Fmt.money(best.total()),
                      Fmt.pct(overcost),
                      Fmt.money(best.total().subtract(oldTotal)),
                      Fmt.money(oldTotal));
    } else {
      fallback = options.size() - 1;
      reasoning =
          "No hay otro proveedor que venda lo pendiente de "
              + po.number()
              + "; se cancela para reprogramar.";
    }

    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("po", po.number());
    vars.put("supplier", po.supplierName());
    vars.put("event", rejected ? "rechazo" : "retraso");
    vars.put("reason", event.reason() == null ? "" : event.reason());
    vars.put("warehouse", warehouse.code());
    vars.put("lines", lines.toString());
    vars.put("days_until_delivery", Math.round(daysUntilNew * 10) / 10.0);
    LlmGateway.Choice choice = llm.choose("po-exception", vars, options, fallback, reasoning);
    LlmGateway.Option chosen =
        options.get(Math.max(0, Math.min(choice.index(), options.size() - 1)));

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("oc", po.number());
    input.put("proveedor", po.supplierCode());
    input.put("evento", event.status());
    input.put("motivo", event.reason());
    input.put("nueva_fecha", po.expectedAt() == null ? null : SimTime.display(po.expectedAt()));
    input.put("lineas", lines);
    input.put("total_pendiente", Money.format(oldTotal));

    Map<String, Object> attributes = new HashMap<>();
    BigDecimal amount = null;
    if (chosen.id().startsWith("reassign:")) {
      Alternative a = alternatives.get(options.indexOf(chosen));
      double overcost = overcostPct(oldTotal, a.total());
      attributes.put("overcost_pct", overcost);
      amount = Money.round(a.total());
    }
    WriteTools.Context ctx =
        new WriteTools.Context(
            "po_exception",
            "purchase_order_" + event.status(),
            "po:" + po.number() + ":" + event.status(),
            input,
            altTable,
            choice.reasoning(),
            choice.confidence(),
            choice.trace(),
            attributes,
            amount,
            List.of(new ActionProposal.Ref("purchase_order", po.id(), po.number())));
    String result =
        tools.withContext(
            ctx,
            () -> {
              if (chosen.id().startsWith("reassign:")) {
                return tools.reassignPurchaseOrder(
                    po.number(), chosen.id().substring(9), choice.reasoning());
              }
              if ("wait".equals(chosen.id())) {
                return tools.waitPurchaseOrder(po.number(), choice.reasoning());
              }
              return tools.cancelPurchaseOrder(po.number(), choice.reasoning());
            });
    log.info("Excepción de {}: {}", po.number(), result);
  }

  private List<Alternative> alternatives(PurchaseOrderDto po, boolean urgent) {
    Map<Long, BigDecimal> totals = new HashMap<>();
    Map<Long, Integer> covered = new HashMap<>();
    java.util.Set<Long> preferred = new java.util.HashSet<>();
    Map<Long, SupplierDto> suppliers = new HashMap<>();
    int pendingLines = 0;
    for (PurchaseOrderDto.Line l : po.lines()) {
      if (l.pending() <= 0) {
        continue;
      }
      pendingLines++;
      for (OfferDto o : purchasing.offersForProduct(l.productId())) {
        SupplierDto s = o.supplier();
        if (s.id() == po.supplierId() || s.leadTimeTo(po.warehouseId()) == null) {
          continue;
        }
        suppliers.put(s.id(), s);
        totals.merge(
            s.id(), o.unitCost().multiply(BigDecimal.valueOf(l.pending())), BigDecimal::add);
        covered.merge(s.id(), 1, Integer::sum);
        if (o.preferred()) {
          preferred.add(s.id());
        }
      }
    }
    List<SupplierScorer.Candidate> candidates = new ArrayList<>();
    for (Map.Entry<Long, Integer> e : covered.entrySet()) {
      if (e.getValue() < pendingLines) {
        continue;
      }
      SupplierDto s = suppliers.get(e.getKey());
      candidates.add(
          new SupplierScorer.Candidate(
              s.id(),
              s.code(),
              s.name(),
              totals.get(s.id()).doubleValue(),
              s.leadTimeTo(po.warehouseId()),
              s.onTimeRate(),
              preferred.contains(s.id())));
    }
    List<Alternative> result = new ArrayList<>();
    SupplierScorer.Weights weights = urgent ? urgentWeights() : settings.current().weights();
    for (SupplierScorer.Scored sc : SupplierScorer.score(candidates, weights)) {
      SupplierDto s = suppliers.get(sc.candidate().supplierId());
      result.add(
          new Alternative(
              s, Money.round(totals.get(s.id())), sc.candidate().leadTimeDays(), sc.total()));
    }
    return result;
  }

  /** En una excepción pesa más llegar rápido que el precio. */
  private SupplierScorer.Weights urgentWeights() {
    SupplierScorer.Weights w = settings.current().weights();
    return new SupplierScorer.Weights(w.price() * 0.6, w.leadTime() * 1.6, w.fulfillment(), 0);
  }

  private static BigDecimal pendingTotal(PurchaseOrderDto po) {
    BigDecimal total = BigDecimal.ZERO;
    for (PurchaseOrderDto.Line l : po.lines()) {
      total = total.add(l.unitCost().multiply(BigDecimal.valueOf(l.pending())));
    }
    return total;
  }

  static double overcostPct(BigDecimal oldTotal, BigDecimal newTotal) {
    if (oldTotal.signum() == 0) {
      return 0;
    }
    return newTotal
        .subtract(oldTotal)
        .multiply(BigDecimal.valueOf(100))
        .divide(oldTotal, 1, RoundingMode.HALF_UP)
        .doubleValue();
  }
}
