package com.xperp.ai.rules;

import com.xperp.shared.persistence.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Impacto económico u operativo de una propuesta de la IA (ADR 0004): qué gasta, qué protege y qué
 * arriesga. Quien aprueba lo ve antes del razonamiento. Funciones puras; las ventas en riesgo son
 * estimaciones con la demanda promedio.
 */
public final class Impact {

  /** Gasto o sobrecosto. */
  public static final String COST = "cost";

  /** Margen, ahorro o venta protegida. */
  public static final String BENEFIT = "benefit";

  /** Lo que se pierde si no se actúa. */
  public static final String RISK = "risk";

  public static final String INFO = "info";

  private Impact() {}

  /** Una cifra del impacto. {@code value} va como string decimal, igual que en el contrato. */
  public record Item(String label, String value, String unit, String effect, String note) {

    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("label", label);
      m.put("value", value);
      m.put("unit", unit);
      m.put("effect", effect);
      if (note != null) {
        m.put("note", note);
      }
      return m;
    }
  }

  public static Item money(String label, BigDecimal value, String effect, String note) {
    return new Item(label, Money.format(Money.round(value)), "cop", effect, note);
  }

  public static Item pct(String label, double value, String effect, String note) {
    return new Item(label, oneDecimal(value), "pct", effect, note);
  }

  public static Item days(String label, double value, String effect, String note) {
    return new Item(label, oneDecimal(value), "days", effect, note);
  }

  public static Item units(String label, long value, String effect, String note) {
    return new Item(label, Long.toString(value), "units", effect, note);
  }

  // ---------------------------------------------------------------- reglas

  /**
   * Unidades que se dejarían de vender si la compra no se hace hoy: la próxima oportunidad es
   * mañana (un día de revisión) y la mercancía tarda el tiempo de entrega en llegar.
   */
  public static long unitsAtRisk(
      double dailyDemand, int leadTimeDays, int available, int inTransit) {
    double need = Math.max(0, dailyDemand) * (leadTimeDays + ReorderRules.REVIEW_PERIOD_DAYS);
    double stock = Math.max(0, available) + Math.max(0, inTransit);
    return (long) Math.max(0, Math.ceil(need - stock));
  }

  /** Una línea de compra con lo necesario para medir su impacto. */
  public record PurchaseLine(
      String sku,
      int quantity,
      BigDecimal unitCost,
      BigDecimal price,
      double dailyDemand,
      int leadTimeDays,
      int available,
      int inTransit) {}

  /** Lo que la OC le carga al presupuesto de una categoría y lo que esa categoría tenía libre. */
  public record BudgetUse(String category, BigDecimal charged, BigDecimal available) {}

  /** Orden de compra: cuánto compromete, cuánto presupuesto usa, qué margen trae y qué protege. */
  public static List<Item> purchase(List<PurchaseLine> lines, List<BudgetUse> budgets) {
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal margin = BigDecimal.ZERO;
    BigDecimal salesAtRisk = BigDecimal.ZERO;
    long unitsAtRisk = 0;
    PurchaseLine critical = null;
    double criticalCover = Double.POSITIVE_INFINITY;
    for (PurchaseLine l : lines) {
      BigDecimal qty = BigDecimal.valueOf(l.quantity());
      total = total.add(l.unitCost().multiply(qty));
      margin = margin.add(l.price().subtract(l.unitCost()).multiply(qty));
      long units = unitsAtRisk(l.dailyDemand(), l.leadTimeDays(), l.available(), l.inTransit());
      unitsAtRisk += units;
      salesAtRisk = salesAtRisk.add(l.price().multiply(BigDecimal.valueOf(units)));
      double cover = ReorderRules.daysOfCover(l.available(), l.dailyDemand());
      if (cover < criticalCover) {
        criticalCover = cover;
        critical = l;
      }
    }
    List<Item> items = new ArrayList<>();
    items.add(money("Compromete", total, COST, null));
    for (BudgetUse b : budgets) {
      if (b.available().signum() <= 0) {
        items.add(
            money(
                "Excede el presupuesto de " + b.category(),
                b.charged(),
                RISK,
                "La categoría ya no tiene disponible este mes"));
        continue;
      }
      double used =
          b.charged()
              .multiply(BigDecimal.valueOf(100))
              .divide(b.available(), 2, RoundingMode.HALF_UP)
              .doubleValue();
      items.add(
          pct(
              "Usa del disponible de " + b.category(),
              used,
              used > 100 ? RISK : INFO,
              "Disponible antes de esta OC: " + Money.display(b.available())));
    }
    items.add(money("Margen bruto esperado al venderlo", margin, BENEFIT, null));
    items.add(
        money(
            "Ventas en riesgo si no se compra hoy",
            salesAtRisk,
            RISK,
            "Estimado: " + unitsAtRisk + " und con la demanda promedio"));
    if (critical != null && critical.dailyDemand() > 0) {
      double onArrival =
          Math.max(
              0,
              (critical.available()
                      + critical.inTransit()
                      + critical.quantity()
                      - critical.dailyDemand() * critical.leadTimeDays())
                  / critical.dailyDemand());
      items.add(
          days(
              "Cobertura al recibir",
              onArrival,
              BENEFIT,
              "%s: hoy alcanza %s días"
                  .formatted(critical.sku(), FinanceRules.pct(criticalCover))));
    }
    return items;
  }

  /** Reasignar una OC a otro proveedor: sobrecosto (o ahorro) y tiempo de entrega. */
  public static List<Item> reassign(
      BigDecimal pendingTotal, BigDecimal newTotal, Integer newLeadTimeDays) {
    List<Item> items = new ArrayList<>();
    items.add(money("Nuevo valor de la OC", newTotal, COST, null));
    BigDecimal diff = newTotal.subtract(pendingTotal);
    String note =
        pendingTotal.signum() == 0
            ? null
            : "%s %% frente a %s"
                .formatted(
                    FinanceRules.pct(
                        diff.multiply(BigDecimal.valueOf(100))
                            .divide(pendingTotal, 2, RoundingMode.HALF_UP)
                            .doubleValue()),
                    Money.display(pendingTotal));
    if (diff.signum() >= 0) {
      items.add(money("Sobrecosto frente a la OC original", diff, COST, note));
    } else {
      items.add(money("Ahorro frente a la OC original", diff.negate(), BENEFIT, note));
    }
    if (newLeadTimeDays != null) {
      items.add(days("Entrega del nuevo proveedor", newLeadTimeDays, INFO, null));
    }
    return items;
  }

  /** Cancelar una OC: libera presupuesto y deja de llegar mercancía. */
  public static List<Item> cancel(BigDecimal total, long pendingUnits) {
    return List.of(
        money("Libera del presupuesto", total, BENEFIT, null),
        units("Unidades que dejan de llegar", pendingUnits, RISK, null));
  }

  /** Traslado entre bodegas: evita una compra y protege ventas en el destino. */
  public static List<Item> transfer(
      int quantity,
      BigDecimal unitCost,
      BigDecimal price,
      double targetDailyDemand,
      int targetAvailable,
      int transferDays,
      double donorCoverAfter) {
    long risk = unitsAtRisk(targetDailyDemand, transferDays, targetAvailable, 0);
    return List.of(
        units("Unidades a trasladar", quantity, INFO, null),
        money(
            "Compra que se evita",
            unitCost.multiply(BigDecimal.valueOf(quantity)),
            BENEFIT,
            "A costo; el flete del traslado lo cobra la transportadora"),
        money(
            "Ventas en riesgo sin el traslado",
            price.multiply(BigDecimal.valueOf(risk)),
            RISK,
            "Estimado: " + risk + " und con la demanda promedio"),
        days("Cobertura que le queda al origen", donorCoverAfter, INFO, null));
  }

  /** Ajuste de inventario por conteo: valor a costo de la diferencia. */
  public static List<Item> adjustment(int delta, BigDecimal unitCost) {
    BigDecimal value = unitCost.multiply(BigDecimal.valueOf(Math.abs(delta)));
    return List.of(
        delta < 0
            ? money("Pérdida de inventario a costo", value, COST, delta + " und")
            : money("Inventario que aparece, a costo", value, BENEFIT, "+" + delta + " und"));
  }

  /** Guía de despacho: flete, su peso en el pedido y cuánto más cuesta que la opción más barata. */
  public static List<Item> shipment(
      BigDecimal cost, BigDecimal orderTotal, BigDecimal cheapest, String cheapestCarrier) {
    List<Item> items = new ArrayList<>();
    items.add(money("Flete", cost, COST, null));
    if (orderTotal != null && orderTotal.signum() > 0) {
      items.add(
          pct(
              "Del valor del pedido",
              cost.multiply(BigDecimal.valueOf(100))
                  .divide(orderTotal, 2, RoundingMode.HALF_UP)
                  .doubleValue(),
              INFO,
              "Pedido por " + Money.display(orderTotal)));
    }
    if (cheapest != null && cost.compareTo(cheapest) > 0) {
      items.add(
          money(
              "Más que la opción más barata",
              cost.subtract(cheapest),
              COST,
              cheapestCarrier == null ? null : "La más barata: " + cheapestCarrier));
    }
    return items;
  }

  /** Plan de compras por presupuesto: lo que se compra y lo que pasa a Gerencia. */
  public static List<Item> budgetPlan(BigDecimal withinBudget, BigDecimal deferred) {
    return List.of(
        money("OC dentro del presupuesto", withinBudget, COST, null),
        money("OC que pasan a aprobación de Gerencia", deferred, INFO, null));
  }

  /** Pedido retenido por anomalía: la venta queda detenida hasta que alguien la revise. */
  public static List<Item> heldOrder(BigDecimal orderTotal) {
    return List.of(money("Venta detenida hasta la revisión", orderTotal, RISK, null));
  }

  /** Producto estancado: capital inmovilizado a costo. */
  public static List<Item> slowMover(int onHand, BigDecimal unitCost, Double daysOfCover) {
    List<Item> items = new ArrayList<>();
    items.add(
        money(
            "Capital inmovilizado a costo",
            unitCost.multiply(BigDecimal.valueOf(Math.max(0, onHand))),
            RISK,
            onHand + " und en bodega"));
    if (daysOfCover != null) {
      items.add(days("Cobertura actual", daysOfCover, INFO, null));
    }
    return items;
  }

  static String oneDecimal(double v) {
    if (Double.isInfinite(v) || Double.isNaN(v)) {
      return "0.0";
    }
    return String.format(Locale.ROOT, "%.1f", v);
  }
}
