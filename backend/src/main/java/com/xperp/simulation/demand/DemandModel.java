package com.xperp.simulation.demand;

import java.time.LocalDate;
import java.util.Random;
import java.util.Set;

/**
 * {@code demanda = base × factor_bodega × día_semana × quincena × festivo × temporada × ruido}
 * (AGENTS.md, 2.10). Funciones puras.
 */
public final class DemandModel {

  private DemandModel() {}

  /** Factores de calendario del día (sin la temporada, que depende de la categoría). */
  public static double dayFactor(LocalDate date, DemandProfile p, Set<LocalDate> holidays) {
    double f = p.weekday().getOrDefault(date.getDayOfWeek(), 1.0);
    if (isPayday(date)) {
      f *= p.payday();
    }
    if (holidays.contains(date)) {
      f *= p.holiday();
    } else if (holidays.contains(date.plusDays(1))) {
      f *= p.holidayEve();
    }
    return f;
  }

  public static boolean isPayday(LocalDate date) {
    int d = date.getDayOfMonth();
    return d == 15 || d == 16 || d == 1 || d == date.lengthOfMonth();
  }

  public static double season(LocalDate date, String categoryCode, DemandProfile p) {
    if (date.getMonthValue() != p.seasonMonth()) {
      return 1.0;
    }
    return p.seasonByCategory().getOrDefault(categoryCode, p.seasonDefault());
  }

  /** Ruido lognormal con media 1: exp(σZ − σ²/2). */
  public static double noise(Random rng, double sigma) {
    return Math.exp(sigma * rng.nextGaussian() - sigma * sigma / 2);
  }

  /** Unidades demandadas de un SKU en una bodega un día. */
  public static int units(
      double base,
      double warehouseFactor,
      LocalDate date,
      String categoryCode,
      DemandProfile p,
      Set<LocalDate> holidays,
      Random rng) {
    double mean =
        base * warehouseFactor * dayFactor(date, p, holidays) * season(date, categoryCode, p);
    return (int) Math.round(mean * noise(rng, p.noiseSigma()));
  }
}
