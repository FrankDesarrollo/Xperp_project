package com.xperp.ai.forecasting;

/**
 * Holt-Winters aditivo con estacionalidad semanal (AGENTS.md, sección 8.2). Ajusta α, β y γ con una
 * búsqueda en rejilla que minimiza el error cuadrático de un paso adelante.
 */
public final class HoltWinters {

  /** Modelo ajustado: nivel, tendencia y estacionalidad al final de la serie. */
  public record Model(
      double alpha,
      double beta,
      double gamma,
      double level,
      double trend,
      double[] season,
      double sse) {

    /** Pronóstico h pasos adelante (h ≥ 1), nunca negativo. */
    public double forecast(int h, int seriesLength) {
      int period = season.length;
      int idx = Math.floorMod(seriesLength + h - 1, period);
      return Math.max(0, level + h * trend + season[idx]);
    }
  }

  private static final double[] ALPHAS = {0.1, 0.2, 0.3, 0.5};
  private static final double[] BETAS = {0.01, 0.05, 0.1};
  private static final double[] GAMMAS = {0.1, 0.2, 0.3};

  private HoltWinters() {}

  /** Ajusta el modelo. La serie debe tener al menos dos periodos completos. */
  public static Model fit(double[] y, int period) {
    if (y.length < 2 * period) {
      throw new IllegalArgumentException("Hacen falta al menos dos periodos completos");
    }
    Model best = null;
    for (double a : ALPHAS) {
      for (double b : BETAS) {
        for (double g : GAMMAS) {
          Model m = run(y, period, a, b, g);
          if (best == null || m.sse() < best.sse()) {
            best = m;
          }
        }
      }
    }
    return best;
  }

  static Model run(double[] y, int period, double alpha, double beta, double gamma) {
    // Inicialización: nivel = promedio del primer periodo; tendencia = cambio promedio entre los
    // dos primeros periodos; estacionalidad = desviación de cada día frente al promedio.
    double first = 0;
    double second = 0;
    for (int i = 0; i < period; i++) {
      first += y[i];
      second += y[i + period];
    }
    first /= period;
    second /= period;
    double level = first;
    double trend = (second - first) / period;
    double[] season = new double[period];
    for (int i = 0; i < period; i++) {
      season[i] = y[i] - first;
    }
    double sse = 0;
    for (int t = period; t < y.length; t++) {
      int s = t % period;
      double predicted = level + trend + season[s];
      double error = y[t] - predicted;
      sse += error * error;
      double previousLevel = level;
      level = alpha * (y[t] - season[s]) + (1 - alpha) * (level + trend);
      trend = beta * (level - previousLevel) + (1 - beta) * trend;
      season[s] = gamma * (y[t] - level) + (1 - gamma) * season[s];
    }
    return new Model(alpha, beta, gamma, level, trend, season, sse);
  }

  /** Media móvil ponderada (pesos lineales) para series cortas. */
  public static double weightedMovingAverage(double[] y, int window) {
    if (y.length == 0) {
      return 0;
    }
    int n = Math.min(window, y.length);
    double sum = 0;
    double weights = 0;
    for (int i = 0; i < n; i++) {
      double w = i + 1;
      sum += w * y[y.length - n + i];
      weights += w;
    }
    return sum / weights;
  }
}
