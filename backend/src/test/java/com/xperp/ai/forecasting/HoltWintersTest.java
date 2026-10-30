package com.xperp.ai.forecasting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.Test;

class HoltWintersTest {

  private static final double[] WEEK = {105, 95, 95, 100, 115, 120, 50};

  private static double[] weeks(int n) {
    double[] y = new double[n * 7];
    for (int i = 0; i < y.length; i++) {
      y[i] = WEEK[i % 7];
    }
    return y;
  }

  @Test
  void learnsWeeklySeasonality() {
    double[] y = weeks(8);
    HoltWinters.Model m = HoltWinters.fit(y, 7);
    // El día siguiente al final de la serie repite el lunes (índice 0)
    for (int h = 1; h <= 7; h++) {
      assertThat(m.forecast(h, y.length)).isCloseTo(WEEK[(h - 1) % 7], offset(3.0));
    }
  }

  @Test
  void followsTrend() {
    double[] y = new double[56];
    for (int i = 0; i < y.length; i++) {
      y[i] = 100 + i + WEEK[i % 7] - 97;
    }
    HoltWinters.Model m = HoltWinters.fit(y, 7);
    // Mismo día de la semana, una semana después: unas 7 unidades más
    assertThat(m.forecast(8, y.length)).isGreaterThan(m.forecast(1, y.length) + 3);
    assertThat(m.trend()).isPositive();
  }

  @Test
  void neverForecastsNegative() {
    double[] y = new double[28];
    y[27] = 5;
    HoltWinters.Model m = HoltWinters.fit(y, 7);
    for (int h = 1; h <= 30; h++) {
      assertThat(m.forecast(h, y.length)).isGreaterThanOrEqualTo(0);
    }
  }

  @Test
  void needsTwoPeriods() {
    assertThatThrownBy(() -> HoltWinters.fit(new double[10], 7))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void weightedMovingAverageFavorsRecentDays() {
    double wma = HoltWinters.weightedMovingAverage(new double[] {10, 10, 10, 40}, 4);
    assertThat(wma).isEqualTo((10 + 20 + 30 + 160) / 10.0);
    assertThat(HoltWinters.weightedMovingAverage(new double[0], 14)).isZero();
  }
}
