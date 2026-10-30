package com.xperp.ai.rules;

import java.util.List;

/**
 * Reglas determinísticas de reabastecimiento (AGENTS.md, sección 8.1). Funciones puras, sin estado
 * ni dependencias, para probarlas aisladas.
 */
public final class ReorderRules {

  /** z para un nivel de servicio del 95 %. */
  public static final double Z_95 = 1.65;

  /** Cobertura objetivo en días. */
  public static final int COVER_DAYS = 10;

  public static final int COVER_DAYS_PERISHABLE = 5;

  /** Días de demanda que se toman como tope de inventario. */
  public static final int MAX_STOCK_DAYS = 30;

  /**
   * Periodo de revisión: el ciclo corre una vez al día, así que el punto de reorden cubre el tiempo
   * de entrega más un día hasta la próxima revisión. Ver ADR 0003.
   */
  public static final int REVIEW_PERIOD_DAYS = 1;

  private ReorderRules() {}

  /** Promedio y desviación estándar muestral de una serie diaria. */
  public record DemandStats(double mean, double std, int days) {

    public static DemandStats of(List<Integer> series) {
      if (series.isEmpty()) {
        return new DemandStats(0, 0, 0);
      }
      double sum = 0;
      for (int v : series) {
        sum += v;
      }
      double mean = sum / series.size();
      double sq = 0;
      for (int v : series) {
        sq += (v - mean) * (v - mean);
      }
      double std = series.size() > 1 ? Math.sqrt(sq / (series.size() - 1)) : 0;
      return new DemandStats(mean, std, series.size());
    }
  }

  /** Stock de seguridad: z × σ_diaria × √lead_time, redondeado hacia arriba. */
  public static int safetyStock(double z, double dailyStd, int leadTimeDays) {
    if (dailyStd <= 0 || leadTimeDays <= 0) {
      return 0;
    }
    return (int) Math.ceil(z * dailyStd * Math.sqrt(leadTimeDays));
  }

  /** Punto de reorden: demanda × (lead_time + revisión) + stock de seguridad. */
  public static int reorderPoint(double dailyDemand, int leadTimeDays, int safetyStock) {
    return (int) Math.ceil(dailyDemand * (leadTimeDays + REVIEW_PERIOD_DAYS)) + safetyStock;
  }

  public static int coverDays(boolean perishable) {
    return perishable ? COVER_DAYS_PERISHABLE : COVER_DAYS;
  }

  /** Tope de inventario: 30 días de demanda. */
  public static int maxStock(double dailyDemand) {
    return (int) Math.ceil(dailyDemand * MAX_STOCK_DAYS);
  }

  /** ¿Hay que pedir? disponible + en tránsito ≤ punto de reorden. */
  public static boolean needsReorder(int available, int inTransit, int reorderPoint) {
    return reorderPoint > 0 && available + inTransit <= reorderPoint;
  }

  /**
   * Cantidad a pedir: pronóstico(lead_time + cobertura) + stock de seguridad − disponible − en
   * tránsito, redondeada hacia arriba a cajas, con mínimo {@code minCases} cajas y tope para que el
   * inventario no pase de {@code maxStock}.
   *
   * @return unidades a pedir (múltiplo de {@code unitsPerCase}), o 0 si no hace falta
   */
  public static int orderQuantity(
      double forecastOverHorizon,
      int safetyStock,
      int available,
      int inTransit,
      int unitsPerCase,
      int minCases,
      int maxStock) {
    double raw = forecastOverHorizon + safetyStock - available - inTransit;
    if (raw <= 0) {
      return 0;
    }
    int cases = (int) Math.ceil(raw / unitsPerCase);
    cases = Math.max(cases, minCases);
    if (maxStock > 0) {
      int room = maxStock - Math.max(0, available) - inTransit;
      int maxCases = Math.max(minCases, (int) Math.floor((double) room / unitsPerCase));
      cases = Math.min(cases, maxCases);
    }
    return cases * unitsPerCase;
  }

  /** Días de cobertura del disponible con la demanda diaria; infinito si no hay demanda. */
  public static double daysOfCover(int available, double dailyDemand) {
    if (dailyDemand <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.max(0, available) / dailyDemand;
  }
}
