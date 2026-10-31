package com.xperp.ai.agent;

import com.xperp.ai.forecasting.ForecastService;
import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.PolicyEngine;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryRow;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Traslados entre bodegas (AGENTS.md, 4.3): si una bodega está en quiebre o riesgo y la otra tiene
 * más de 30 días de cobertura del SKU, propone trasladar en vez de comprar (nivel 1, lo aprueba
 * gerencia). Los traslados van con TRP-02 y tardan 2 días.
 */
@Component
class TransferAgent {

  static final double DONOR_MIN_DAYS = 30;
  static final int TRANSFER_DAYS = 2;

  /** Días de cobertura que la bodega de origen conserva después del traslado. */
  static final int DONOR_KEEP_DAYS = 20;

  private final CatalogService catalog;
  private final ForecastService forecasts;
  private final LlmGateway llm;
  private final ActionGateway gateway;

  TransferAgent(
      CatalogService catalog, ForecastService forecasts, LlmGateway llm, ActionGateway gateway) {
    this.catalog = catalog;
    this.forecasts = forecasts;
    this.llm = llm;
    this.gateway = gateway;
  }

  /** Bodega que puede ceder el SKU: más de 30 días de cobertura. */
  static InventoryRow donor(List<InventoryRow> rows, InventoryRow target) {
    for (InventoryRow r : rows) {
      if (r.stock().productId() != target.stock().productId()
          || r.stock().warehouseId() == target.stock().warehouseId()) {
        continue;
      }
      Double cover = r.stock().daysOfCover();
      if (cover != null && cover > DONOR_MIN_DAYS) {
        return r;
      }
    }
    return null;
  }

  /** Cuánto trasladar y con qué pronóstico se calculó. */
  record Plan(int quantity, int horizon, double forecast) {}

  /**
   * Lo que cubre el horizonte en destino (traslado + cobertura objetivo + stock de seguridad), en
   * cajas, sin dejar al origen con menos de 20 días. Una cantidad de 0 o menos: no hay traslado.
   */
  static Plan plan(
      ForecastService forecasts,
      InventoryRow target,
      InventoryRow donor,
      ProductDto p,
      LocalDate today) {
    int horizon = TRANSFER_DAYS + target.stock().coverDays();
    Double fc = forecasts.sum(p.id(), target.stock().warehouseId(), today, horizon);
    double daily = target.stock().avgDailyDemand().doubleValue();
    double forecast = fc == null ? daily * horizon : fc;
    double need = forecast + target.stock().safetyStock() - Math.max(0, target.stock().available());
    int qty = p.roundUpToCases((int) Math.ceil(need));
    double donorDaily = donor.stock().avgDailyDemand().doubleValue();
    int maxGive = donor.stock().available() - (int) Math.ceil(donorDaily * DONOR_KEEP_DAYS);
    maxGive = (maxGive / p.unitsPerCase()) * p.unitsPerCase();
    return new Plan(Math.min(qty, maxGive), horizon, forecast);
  }

  boolean propose(InventoryRow target, InventoryRow donor, LocalDate today, String triggerEvent) {
    ProductDto p = catalog.product(target.stock().productId());
    Plan plan = plan(forecasts, target, donor, p, today);
    int qty = plan.quantity();
    int horizon = plan.horizon();
    double forecast = plan.forecast();
    double daily = target.stock().avgDailyDemand().doubleValue();
    if (qty <= 0) {
      return false;
    }
    double donorDaily = donor.stock().avgDailyDemand().doubleValue();
    double donorAfter =
        donorDaily <= 0 ? Double.POSITIVE_INFINITY : (donor.stock().available() - qty) / donorDaily;

    String facts =
        ("%s en %s está en %s (%s und disponibles, demanda de %s und/día) y %s tiene %s und, %s de"
                + " cobertura. Trasladar %s und con Envíos Cordillera (2 días) cubre %d días en %s y"
                + " deja %s en %s, sin comprar ni comprometer presupuesto.")
            .formatted(
                p.name(),
                target.warehouseCode(),
                target.stock().available() <= 0 ? "quiebre" : "riesgo de quiebre",
                Fmt.units(Math.max(0, target.stock().available())),
                Fmt.dec(daily),
                donor.warehouseCode(),
                Fmt.units(donor.stock().available()),
                Fmt.days(donor.stock().daysOfCover()),
                Fmt.units(qty),
                horizon,
                target.warehouseCode(),
                Fmt.days(donorAfter),
                donor.warehouseCode());
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("sku", p.sku());
    vars.put("product", p.name());
    vars.put("facts", facts);
    LlmGateway.Text text = llm.write("transfer", vars, facts);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("product_id", p.id());
    payload.put("sku", p.sku());
    payload.put("from_warehouse_id", donor.stock().warehouseId());
    payload.put("from_warehouse_code", donor.warehouseCode());
    payload.put("to_warehouse_id", target.stock().warehouseId());
    payload.put("to_warehouse_code", target.warehouseCode());
    payload.put("quantity", qty);

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("destino", stock(target));
    input.put("origen", stock(donor));
    input.put("pronostico_destino", Math.round(forecast));
    input.put("horizonte_dias", horizon);

    ActionGateway.Outcome outcome =
        gateway.submit(
            ActionProposal.builder("stock_transfer", "propose_stock_transfer")
                .title(
                    "Trasladar %s de %s a %s"
                        .formatted(p.sku(), donor.warehouseCode(), target.warehouseCode()))
                .trigger(triggerEvent, "transfer:" + p.sku() + ":" + target.warehouseCode())
                .input(input)
                .alternatives(
                    List.of(
                        Map.of("opcion", "trasladar", "cantidad", qty, "costo", "flete interno"),
                        Map.of(
                            "opcion",
                            "comprar",
                            "nota",
                            "compromete presupuesto y tarda el tiempo de entrega del proveedor")))
                .payload(payload)
                .reasoning(text.text())
                .confidence(0.85)
                .llm(text.trace())
                .ref("product", p.id(), p.sku())
                .build());
    return !PolicyEngine.BLOCKED.equals(outcome.policyResult());
  }

  private static Map<String, Object> stock(InventoryRow r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("bodega", r.warehouseCode());
    m.put("disponible", r.stock().available());
    m.put("demanda_diaria", r.stock().avgDailyDemand());
    Double cover = r.stock().daysOfCover();
    m.put("dias_cobertura", cover == null ? null : Math.round(cover * 10) / 10.0);
    return m;
  }
}
