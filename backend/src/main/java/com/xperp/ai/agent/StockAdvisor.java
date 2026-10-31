package com.xperp.ai.agent;

import com.xperp.ai.decisions.AiDecision;
import com.xperp.ai.decisions.AiDecisionRepository;
import com.xperp.ai.forecasting.ForecastService;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.rules.ReorderRules;
import com.xperp.ai.rules.StockAdvice;
import com.xperp.ai.rules.StockAdvice.Kind;
import com.xperp.ai.rules.StockAdvice.Priority;
import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockKey;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.OpenLine;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomendaciones de la IA sobre el inventario: qué haría hoy el ciclo de reabastecimiento con cada
 * existencia, explicado. Solo lee: no crea OC, no propone traslados, no deja decisiones en la
 * bitácora y no llama al LLM (en un empate de proveedores recomienda el de mayor puntaje).
 *
 * <p>Usa las piezas del agente —{@link ReorderRules}, {@link SupplierScorer}, el pronóstico y el
 * plan de traslado de {@link TransferAgent}— para que la recomendación coincida con lo que la IA
 * ejecuta. Lo único que no anticipa es la consolidación por proveedor y la priorización por
 * presupuesto, que dependen de todo el plan del día.
 */
@Service
public class StockAdvisor {

  /** Una recomendación para una existencia (producto en una bodega). */
  public record Advice(
      InventoryRow row,
      Kind kind,
      Priority priority,
      Integer quantity,
      Double daysOfCover,
      String supplierCode,
      String supplierName,
      Integer leadTimeDays,
      BigDecimal amount,
      String sourceWarehouseCode,
      Instant expectedAt,
      String title,
      String reason) {}

  private final InventoryService inventory;
  private final CatalogService catalog;
  private final PurchasingService purchasing;
  private final ForecastService forecasts;
  private final AiSettingsService settings;
  private final AiDecisionRepository decisions;
  private final Clock clock;

  StockAdvisor(
      InventoryService inventory,
      CatalogService catalog,
      PurchasingService purchasing,
      ForecastService forecasts,
      AiSettingsService settings,
      AiDecisionRepository decisions,
      Clock clock) {
    this.inventory = inventory;
    this.catalog = catalog;
    this.purchasing = purchasing;
    this.forecasts = forecasts;
    this.settings = settings;
    this.decisions = decisions;
    this.clock = clock;
  }

  /** Recomendaciones de la más urgente a la menos; filtros opcionales por bodega y producto. */
  @Transactional(readOnly = true)
  public List<Advice> recommendations(Long warehouseId, Long productId) {
    LocalDate today = SimTime.today(clock);
    List<InventoryRow> rows = inventory.rows(null);
    Map<StockKey, OpenLine> openPo = new HashMap<>();
    for (OpenLine l : purchasing.openLines()) {
      // El agente no pide si ya hay una OC abierta, así que suele haber una sola por existencia.
      openPo.putIfAbsent(new StockKey(l.productId(), l.warehouseId()), l);
    }
    Set<StockKey> pending = pendingKeys();

    List<Advice> result = new ArrayList<>();
    for (InventoryRow row : rows) {
      StockKey key = row.stock().key();
      if ((warehouseId != null && key.warehouseId() != warehouseId)
          || (productId != null && key.productId() != productId)) {
        continue;
      }
      ProductDto product = catalog.product(key.productId());
      if (!product.active() || row.stock().reorderBlocked()) {
        continue;
      }
      Advice advice = advise(row, rows, product, openPo.get(key), pending.contains(key), today);
      if (advice != null) {
        result.add(advice);
      }
    }
    result.sort(
        Comparator.comparing(Advice::priority)
            .thenComparing(a -> a.daysOfCover() == null ? Double.MAX_VALUE : a.daysOfCover()));
    return result;
  }

  private Advice advise(
      InventoryRow row,
      List<InventoryRow> rows,
      ProductDto product,
      OpenLine openLine,
      boolean hasPending,
      LocalDate today) {
    int available = row.stock().available();
    double daily = row.stock().avgDailyDemand().doubleValue();
    double cover = ReorderRules.daysOfCover(available, daily);
    Double shownCover = Double.isInfinite(cover) ? null : Math.round(cover * 10) / 10.0;

    // El plan de traslado consulta el pronóstico: solo se calcula si las reglas eligen trasladar.
    InventoryRow donor = TransferAgent.donor(rows, row);
    Kind kind = kind(row, cover, openLine != null, hasPending, donor != null);
    TransferAgent.Plan transfer = null;
    if (kind == Kind.TRANSFER) {
      transfer = TransferAgent.plan(forecasts, row, donor, product, today);
      if (transfer.quantity() <= 0) {
        kind = kind(row, cover, openLine != null, hasPending, false);
      }
    }
    if (kind == null) {
      return null;
    }
    Priority priority = StockAdvice.priority(kind, available, cover);
    String situation = situation(row, product, cover);

    return switch (kind) {
      case PENDING_APPROVAL ->
          new Advice(
              row,
              kind,
              priority,
              null,
              shownCover,
              null,
              null,
              null,
              null,
              null,
              null,
              "Revisar la propuesta de la IA para " + product.sku(),
              situation
                  + " La IA ya propuso reponerlo y la propuesta espera aprobación en la bandeja de"
                  + " aprobaciones.");
      case INCOMING ->
          new Advice(
              row,
              kind,
              priority,
              openLine.pendingQuantity(),
              shownCover,
              null,
              null,
              null,
              null,
              null,
              openLine.expectedAt(),
              "Esperar la %s con %s und"
                  .formatted(openLine.number(), Fmt.units(openLine.pendingQuantity())),
              situation
                  + " Ya hay una OC abierta (%s, %s) con %s und pendientes; pedir otra vez"
                      .formatted(
                          openLine.number(),
                          openLine.status(),
                          Fmt.units(openLine.pendingQuantity()))
                  + " duplicaría la compra.");
      case TRANSFER ->
          new Advice(
              row,
              kind,
              priority,
              transfer.quantity(),
              shownCover,
              null,
              null,
              TransferAgent.TRANSFER_DAYS,
              null,
              donor.warehouseCode(),
              null,
              "Trasladar %s und desde %s"
                  .formatted(Fmt.units(transfer.quantity()), donor.warehouseCode()),
              situation
                  + " %s tiene %s de cobertura: trasladar %s und cubre %d días aquí sin comprar ni"
                      .formatted(
                          donor.warehouseCode(),
                          Fmt.days(donor.stock().daysOfCover()),
                          Fmt.units(transfer.quantity()),
                          transfer.horizon())
                  + " comprometer presupuesto.");
      case BUY -> buy(row, product, kind, priority, shownCover, situation, today);
      case EXCESS -> {
        int over = available - row.stock().maxStock();
        yield new Advice(
            row,
            kind,
            priority,
            over,
            shownCover,
            null,
            null,
            null,
            null,
            null,
            null,
            "No reponer: sobran %s und".formatted(Fmt.units(over)),
            "%s %s en %s tiene %s und disponibles, %s por encima del tope de %s (30 días de"
                    .formatted(
                        product.sku(),
                        product.name(),
                        row.warehouseCode(),
                        Fmt.units(available),
                        Fmt.units(over),
                        Fmt.units(row.stock().maxStock()))
                + " demanda). La IA no lo repondrá hasta que baje del punto de reorden"
                + (row.stock().promotionSuggested()
                    ? "; además sugiere una promoción para rotarlo."
                    : "."));
      }
    };
  }

  private Advice buy(
      InventoryRow row,
      ProductDto product,
      Kind kind,
      Priority priority,
      Double shownCover,
      String situation,
      LocalDate today) {
    StockKey key = row.stock().key();
    List<OfferDto> offers =
        purchasing.offersForProduct(product.id()).stream()
            .filter(o -> o.supplier().leadTimeTo(key.warehouseId()) != null)
            .toList();
    if (offers.isEmpty()) {
      return new Advice(
          row,
          kind,
          priority,
          null,
          shownCover,
          null,
          null,
          null,
          null,
          null,
          null,
          "Conseguir proveedor para " + product.sku(),
          situation + " Ningún proveedor tiene oferta para esta bodega.");
    }
    Map<Long, OfferDto> offerBySupplier = new HashMap<>();
    List<SupplierScorer.Candidate> candidates = new ArrayList<>();
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
    List<SupplierScorer.Scored> scored =
        SupplierScorer.score(candidates, settings.current().weights());
    SupplierScorer.Scored best = scored.get(0);
    OfferDto offer = offerBySupplier.get(best.candidate().supplierId());
    int leadTime = best.candidate().leadTimeDays();
    int horizon = leadTime + row.stock().coverDays();
    Double fc = forecasts.sum(product.id(), key.warehouseId(), today, horizon);
    double forecast = fc == null ? row.stock().avgDailyDemand().doubleValue() * horizon : fc;
    int qty =
        ReorderRules.orderQuantity(
            forecast,
            row.stock().safetyStock(),
            row.stock().available(),
            row.inTransit(),
            product.unitsPerCase(),
            offer.minOrderCases(),
            row.stock().maxStock());
    if (qty <= 0) {
      return null;
    }
    BigDecimal amount = offer.unitCost().multiply(BigDecimal.valueOf(qty));
    String winner =
        scored.size() > 1
            ? " %s tiene el mejor puntaje (%s frente a %s de %s%s)."
                .formatted(
                    best.candidate().supplierName(),
                    Fmt.score(best.total()),
                    Fmt.score(scored.get(1).total()),
                    scored.get(1).candidate().supplierName(),
                    SupplierScorer.isTie(scored) ? "; es un empate técnico" : "")
            : " %s es el único proveedor con oferta.".formatted(best.candidate().supplierName());
    return new Advice(
        row,
        kind,
        priority,
        qty,
        shownCover,
        best.candidate().supplierCode(),
        best.candidate().supplierName(),
        leadTime,
        amount,
        null,
        null,
        "Comprar %s und a %s".formatted(Fmt.units(qty), best.candidate().supplierName()),
        situation
            + " Pedir %s und por %s cubre %d días (pronóstico de %s und), con entrega en %d %s."
                .formatted(
                    Fmt.units(qty),
                    Fmt.money(amount),
                    horizon,
                    Fmt.units(Math.round(forecast)),
                    leadTime,
                    leadTime == 1 ? "día" : "días")
            + winner);
  }

  private static Kind kind(
      InventoryRow row, double cover, boolean openPo, boolean pending, boolean canTransfer) {
    return StockAdvice.kind(
        row.stock().available(),
        row.inTransit(),
        row.stock().reorderPoint(),
        row.stock().maxStock(),
        cover,
        openPo,
        pending,
        canTransfer);
  }

  /** «ABA-007 Aceite vegetal 1 L en CAL-01: 260 und disponibles (2,2 días) frente a un punto…». */
  private static String situation(InventoryRow row, ProductDto product, double cover) {
    return "%s %s en %s: %s und disponibles (%s)%s frente a un punto de reorden de %s."
        .formatted(
            product.sku(),
            product.name(),
            row.warehouseCode(),
            Fmt.units(Math.max(0, row.stock().available())),
            Double.isInfinite(cover) ? "sin demanda" : Fmt.days(cover),
            row.inTransit() > 0 ? " y " + Fmt.units(row.inTransit()) + " en tránsito" : "",
            Fmt.units(row.stock().reorderPoint()));
  }

  /** Existencias con una compra o un traslado de la IA esperando aprobación. */
  private Set<StockKey> pendingKeys() {
    Set<StockKey> keys = new HashSet<>();
    for (AiDecision d :
        decisions.search(null, "pending_approval", null, PageRequest.of(0, 200)).getContent()) {
      Map<String, Object> action = d.getProposedAction();
      if ("create_purchase_order".equals(d.getActionType())
          && action.get("warehouse_id") instanceof Number wh
          && action.get("lines") instanceof List<?> lines) {
        for (Object line : lines) {
          if (line instanceof Map<?, ?> m && m.get("product_id") instanceof Number p) {
            keys.add(new StockKey(p.longValue(), wh.longValue()));
          }
        }
      } else if ("propose_stock_transfer".equals(d.getActionType())
          && action.get("to_warehouse_id") instanceof Number wh
          && action.get("product_id") instanceof Number p) {
        keys.add(new StockKey(p.longValue(), wh.longValue()));
      }
    }
    return keys;
  }
}
