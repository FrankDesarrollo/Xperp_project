package com.xperp.ai.agent;

import com.xperp.ai.policies.ActionProposal;
import com.xperp.catalog.api.ProductDto;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construcción de propuestas de OC (create_purchase_order) a partir de un plan de compra. */
final class PurchaseProposals {

  private PurchaseProposals() {}

  /** Línea planeada: qué SKU, cuánto, a qué costo y por qué. */
  record PlannedLine(
      ProductDto product,
      int quantity,
      BigDecimal unitCost,
      int available,
      int inTransit,
      int reorderPoint,
      int safetyStock,
      double dailyDemand,
      double forecastHorizon,
      int horizonDays,
      double daysOfCover,
      List<Map<String, Object>> scores,
      String winnerReason,
      ActionProposal.LlmTrace llm) {

    BigDecimal amount() {
      return unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    /** Margen bruto por unidad vendida. */
    BigDecimal unitMargin() {
      return product.price().subtract(unitCost);
    }
  }

  /** Plan de una OC: proveedor, bodega y líneas. */
  record Plan(
      SupplierDto supplier, long warehouseId, String warehouseCode, List<PlannedLine> lines) {

    BigDecimal total() {
      return lines.stream().map(PlannedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    Map<String, Object> amountsByCategory() {
      Map<String, Object> m = new LinkedHashMap<>();
      Map<Long, BigDecimal> sums = new LinkedHashMap<>();
      for (PlannedLine l : lines) {
        sums.merge(l.product().categoryId(), l.amount(), BigDecimal::add);
      }
      sums.forEach((k, v) -> m.put(String.valueOf(k), Money.format(v)));
      return m;
    }
  }

  static String idempotencyKey(Plan plan, String triggerRef) {
    StringBuilder sb = new StringBuilder();
    sb.append(TenantContext.require())
        .append('|')
        .append(plan.supplier().code())
        .append('|')
        .append(plan.warehouseCode())
        .append('|')
        .append(triggerRef);
    plan.lines().forEach(l -> sb.append('|').append(l.product().sku()));
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static Map<String, Object> payload(Plan plan, String idempotencyKey) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("supplier_id", plan.supplier().id());
    payload.put("supplier_code", plan.supplier().code());
    payload.put("warehouse_id", plan.warehouseId());
    payload.put("warehouse_code", plan.warehouseCode());
    List<Map<String, Object>> lines = new ArrayList<>();
    for (PlannedLine l : plan.lines()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("product_id", l.product().id());
      m.put("sku", l.product().sku());
      m.put("quantity", l.quantity());
      m.put("unit_cost", Money.format(l.unitCost()));
      lines.add(m);
    }
    payload.put("lines", lines);
    payload.put("total", Money.format(plan.total()));
    payload.put("idempotency_key", idempotencyKey);
    return payload;
  }

  static Map<String, Object> input(Plan plan, Map<String, Object> extra) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("bodega", plan.warehouseCode());
    List<Map<String, Object>> lines = new ArrayList<>();
    for (PlannedLine l : plan.lines()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("sku", l.product().sku());
      m.put("producto", l.product().name());
      m.put("disponible", l.available());
      m.put("en_transito", l.inTransit());
      m.put("punto_reorden", l.reorderPoint());
      m.put("stock_seguridad", l.safetyStock());
      m.put("demanda_diaria", Math.round(l.dailyDemand() * 10) / 10.0);
      m.put(
          "dias_cobertura",
          Double.isInfinite(l.daysOfCover()) ? null : Math.round(l.daysOfCover() * 10) / 10.0);
      m.put("pronostico_horizonte", Math.round(l.forecastHorizon()));
      m.put("horizonte_dias", l.horizonDays());
      m.put("cantidad", l.quantity());
      lines.add(m);
    }
    input.put("lineas", lines);
    input.putAll(extra);
    return input;
  }

  static List<Map<String, Object>> alternatives(Plan plan) {
    List<Map<String, Object>> alts = new ArrayList<>();
    for (PlannedLine l : plan.lines()) {
      for (Map<String, Object> s : l.scores()) {
        Map<String, Object> m = new LinkedHashMap<>(s);
        m.put("sku", l.product().sku());
        alts.add(m);
      }
    }
    return alts;
  }

  /** Razonamiento de plantilla con los números (sin LLM). */
  static String reasoning(Plan plan, String prefix) {
    StringBuilder sb = new StringBuilder();
    if (prefix != null && !prefix.isBlank()) {
      sb.append(prefix).append(' ');
    }
    sb.append("OC a ")
        .append(plan.supplier().name())
        .append(" para ")
        .append(plan.warehouseCode())
        .append(" por ")
        .append(Fmt.money(plan.total()))
        .append(" con ")
        .append(plan.lines().size())
        .append(plan.lines().size() == 1 ? " SKU. " : " SKU. ");
    List<PlannedLine> top =
        plan.lines().stream()
            .sorted((a, b) -> Double.compare(a.daysOfCover(), b.daysOfCover()))
            .limit(3)
            .toList();
    for (PlannedLine l : top) {
      sb.append(l.product().name())
          .append(": disponible ")
          .append(Fmt.units(l.available()))
          .append(" und (")
          .append(Fmt.days(l.daysOfCover()))
          .append(") frente a un punto de reorden de ")
          .append(Fmt.units(l.reorderPoint()))
          .append("; se piden ")
          .append(Fmt.units(l.quantity()))
          .append(" und para cubrir ")
          .append(l.horizonDays())
          .append(" días (pronóstico ")
          .append(Fmt.units(Math.round(l.forecastHorizon())))
          .append(" und). ");
    }
    if (plan.lines().size() > 3) {
      sb.append("Y ").append(plan.lines().size() - 3).append(" SKU más. ");
    }
    String why = top.isEmpty() ? null : top.get(0).winnerReason();
    if (why != null) {
      sb.append(why);
    }
    return sb.toString().trim();
  }

  static String title(Plan plan) {
    String skus =
        plan.lines().size() == 1
            ? plan.lines().get(0).product().sku()
            : plan.lines().size() + " SKU";
    return "Reabastecer %s en %s con %s"
        .formatted(skus, plan.warehouseCode(), plan.supplier().code());
  }

  static ActionProposal.Builder builder(
      Plan plan, String decisionType, String triggerEvent, String triggerRef) {
    String key = idempotencyKey(plan, triggerRef);
    ActionProposal.Builder b =
        ActionProposal.builder(decisionType, "create_purchase_order")
            .title(title(plan))
            .trigger(triggerEvent, triggerRef)
            .payload(payload(plan, key))
            .alternatives(alternatives(plan))
            .amount(Money.round(plan.total()))
            .attribute("amounts_by_category", plan.amountsByCategory());
    for (PlannedLine l : plan.lines()) {
      b.ref("product", l.product().id(), l.product().sku());
    }
    return b;
  }
}
