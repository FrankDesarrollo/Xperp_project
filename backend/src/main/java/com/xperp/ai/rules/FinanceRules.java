package com.xperp.ai.rules;

import com.xperp.dashboard.api.DashboardQueries.BudgetLine;
import com.xperp.dashboard.api.DashboardQueries.FinanceSnapshot;
import com.xperp.shared.persistence.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hallazgos financieros a partir de las cifras del periodo (ADR 0004). Son reglas: no hace falta un
 * LLM para saber que un presupuesto se va a pasar o que hay pagos vencidos. El LLM, si está, solo
 * redacta el resumen. Ninguna recomendación se ejecuta sola: las decide una persona.
 */
public final class FinanceRules {

  /** Uso del presupuesto a partir del cual se avisa (el mismo umbral de priorización, 8.4). */
  public static final double BUDGET_WARNING_PCT = 85;

  /** Proyección a partir de la cual el sobrecosto se considera grave. */
  public static final double BUDGET_SEVERE_PCT = 110;

  /** Capital en exceso o estancado, como % del inventario, a partir del cual se avisa. */
  public static final double EXCESS_CAPITAL_PCT = 3;

  /** Fletes como % de las ventas a partir del cual se avisa. */
  public static final double FREIGHT_WARNING_PCT = 8;

  /** Ventas perdidas como % de las ventas: aviso y grave. */
  public static final double LOST_SALES_WARNING_PCT = 2;

  public static final double LOST_SALES_SEVERE_PCT = 5;

  /** Margen bruto por debajo del cual se avisa. */
  public static final double MARGIN_WARNING_PCT = 18;

  public static final String HIGH = "high";
  public static final String MEDIUM = "medium";
  public static final String LOW = "low";

  private FinanceRules() {}

  /**
   * Un hallazgo. {@code subject} distingue hallazgos del mismo código (la categoría del
   * presupuesto) para no repetir el mismo aviso por condición.
   */
  public record Finding(
      String code,
      String subject,
      String severity,
      String title,
      String detail,
      String recommendation,
      BigDecimal impact,
      String link) {

    public String key() {
      return subject == null ? code : code + ":" + subject;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("code", code);
      m.put("severity", severity);
      m.put("title", title);
      m.put("detail", detail);
      m.put("recommendation", recommendation);
      m.put("impact", impact == null ? null : Money.format(impact));
      m.put("link", link);
      return m;
    }
  }

  public static List<Finding> findings(FinanceSnapshot s) {
    List<Finding> out = new ArrayList<>();
    for (BudgetLine b : s.budgets()) {
      if (b.amount().signum() <= 0) {
        continue;
      }
      BigDecimal used = b.committed().add(b.spent());
      if (b.projectedPct() > 100) {
        boolean severe = b.usedPct() >= 100 || b.projectedPct() >= BUDGET_SEVERE_PCT;
        out.add(
            new Finding(
                "budget_overrun_projected",
                b.categoryCode(),
                severe ? HIGH : MEDIUM,
                "Presupuesto de %s: se proyecta al %s %% al cierre del mes"
                    .formatted(b.categoryName(), pct(b.projectedPct())),
                ("Lleva %s %% (%s de %s) el día %d de %d. Al ritmo de la demanda faltarían %s.")
                    .formatted(
                        pct(b.usedPct()),
                        Money.display(used),
                        Money.display(b.amount()),
                        s.daysElapsed(),
                        s.daysInPeriod(),
                        Money.display(b.projectedOverrun())),
                ("Prioriza las OC de %s por margen y días de cobertura, y decide si se amplía el"
                        + " presupuesto o se aplaza la reposición de lo que tiene más cobertura.")
                    .formatted(b.categoryName()),
                b.projectedOverrun(),
                "/finanzas"));
      } else if (b.usedPct() >= BUDGET_WARNING_PCT) {
        out.add(
            new Finding(
                "budget_near_limit",
                b.categoryCode(),
                MEDIUM,
                "Presupuesto de %s al %s %%".formatted(b.categoryName(), pct(b.usedPct())),
                "Quedan %s de %s para el resto del mes."
                    .formatted(
                        Money.display(b.amount().subtract(used).max(BigDecimal.ZERO)),
                        Money.display(b.amount())),
                "Revisa las OC pendientes de %s antes de aprobarlas.".formatted(b.categoryName()),
                b.amount().subtract(used).max(BigDecimal.ZERO),
                "/ia/aprobaciones"));
      }
    }
    if (s.payablesOverdue().signum() > 0) {
      out.add(
          new Finding(
              "payables_overdue",
              null,
              HIGH,
              "Cuentas por pagar vencidas: " + Money.display(s.payablesOverdue()),
              "Hay pagos a proveedores con la fecha de vencimiento ya pasada.",
              "Programa el pago o renegocia el plazo: un atraso afecta el cumplimiento y el crédito"
                  + " con el proveedor.",
              s.payablesOverdue(),
              "/compras"));
    }
    if (s.payablesDue7d().signum() > 0) {
      out.add(
          new Finding(
              "payables_due_soon",
              null,
              LOW,
              "Pagos que vencen en los próximos 7 días: " + Money.display(s.payablesDue7d()),
              "Cuentas por pagar abiertas: " + Money.display(s.payablesOpen()) + " en total.",
              "Reserva la caja para esos pagos.",
              s.payablesDue7d(),
              "/compras"));
    }
    BigDecimal tied = s.inventoryExcessValue().max(s.slowMoverValue());
    if (s.inventoryValue().signum() > 0 && share(tied, s.inventoryValue()) >= EXCESS_CAPITAL_PCT) {
      out.add(
          new Finding(
              "excess_inventory",
              null,
              MEDIUM,
              "Capital inmovilizado en inventario: " + Money.display(tied),
              ("%s %% del inventario a costo (%s): %s por encima del tope de cada existencia y %s"
                      + " en productos con más de 60 días de cobertura.")
                  .formatted(
                      pct(share(tied, s.inventoryValue())),
                      Money.display(s.inventoryValue()),
                      Money.display(s.inventoryExcessValue()),
                      Money.display(s.slowMoverValue())),
              "Frena la recompra de lo que está en exceso y considera una promoción en lo estancado"
                  + " para liberar caja.",
              tied,
              "/inventario"));
    }
    if (s.revenue().signum() > 0) {
      if (s.freightPct() >= FREIGHT_WARNING_PCT) {
        out.add(
            new Finding(
                "freight_high",
                null,
                MEDIUM,
                "Fletes en el %s %% de las ventas".formatted(pct(s.freightPct())),
                "Fletes por %s frente a ventas por %s."
                    .formatted(Money.display(s.freight()), Money.display(s.revenue())),
                "Consolida despachos por ciudad y revisa la transportadora en las rutas donde una"
                    + " opción más barata cumple igual.",
                s.freight(),
                "/despachos"));
      }
      double lostPct = share(s.lostSalesAmount(), s.revenue());
      if (lostPct >= LOST_SALES_WARNING_PCT) {
        out.add(
            new Finding(
                "lost_sales_high",
                null,
                lostPct >= LOST_SALES_SEVERE_PCT ? HIGH : MEDIUM,
                "Ventas perdidas por falta de stock: " + Money.display(s.lostSalesAmount()),
                "Equivalen al %s %% de las ventas del periodo.".formatted(pct(lostPct)),
                "Aprueba a tiempo las OC urgentes y revisa las aprobaciones que vencieron sin"
                    + " respuesta.",
                s.lostSalesAmount(),
                "/ia/aprobaciones"));
      }
      if (s.grossMarginPct() < MARGIN_WARNING_PCT) {
        out.add(
            new Finding(
                "margin_low",
                null,
                MEDIUM,
                "Margen bruto en el %s %%".formatted(pct(s.grossMarginPct())),
                "Margen de %s sobre ventas por %s."
                    .formatted(Money.display(s.grossMargin()), Money.display(s.revenue())),
                "Revisa los descuentos por segmento y los costos de los proveedores que subieron.",
                null,
                "/compras"));
      }
    }
    out.sort(Comparator.comparingInt((Finding f) -> rank(f.severity())));
    return out;
  }

  /** Los números del periodo en una frase, para el resumen con o sin LLM. */
  public static String facts(FinanceSnapshot s, List<Finding> findings) {
    StringBuilder sb =
        new StringBuilder(
            ("Del %s al %s: ventas %s, margen bruto %s (%s %%), fletes %s, compras recibidas %s y"
                    + " comprometidas %s, cuentas por pagar abiertas %s, inventario a costo %s.")
                .formatted(
                    s.from(),
                    s.to(),
                    Money.display(s.revenue()),
                    Money.display(s.grossMargin()),
                    pct(s.grossMarginPct()),
                    Money.display(s.freight()),
                    Money.display(s.purchasesReceived()),
                    Money.display(s.purchasesCommitted()),
                    Money.display(s.payablesOpen()),
                    Money.display(s.inventoryValue())));
    if (findings.isEmpty()) {
      sb.append(" Sin hallazgos: presupuesto, pagos, inventario, fletes y margen en rango.");
    } else {
      sb.append(" Hallazgos: ");
      for (int i = 0; i < findings.size(); i++) {
        sb.append(i == 0 ? "" : "; ").append(findings.get(i).title());
      }
      sb.append('.');
    }
    return sb.toString();
  }

  private static int rank(String severity) {
    return switch (severity) {
      case HIGH -> 0;
      case MEDIUM -> 1;
      default -> 2;
    };
  }

  private static double share(BigDecimal part, BigDecimal whole) {
    if (whole.signum() == 0) {
      return 0;
    }
    return part.multiply(BigDecimal.valueOf(100))
        .divide(whole, 2, RoundingMode.HALF_UP)
        .doubleValue();
  }

  /** Porcentaje para leer, con coma decimal: 12,5. */
  static String pct(double v) {
    return new DecimalFormat(
            "#,##0.#", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO")))
        .format(v);
  }
}
