package com.xperp.ai.anomalies;

import static org.assertj.core.api.Assertions.assertThat;

import com.xperp.sales.api.CustomerSkuStats;
import org.junit.jupiter.api.Test;

class DemandAnomalyDetectorTest {

  private static final CustomerSkuStats HISTORY = new CustomerSkuStats(1, 20, 50, 80);

  @Test
  void tenTimesTheUsualIsAnomalous() {
    assertThat(DemandAnomalyDetector.isAnomalous(500, HISTORY)).isTrue();
  }

  @Test
  void normalVariationIsNotAnomalous() {
    assertThat(DemandAnomalyDetector.isAnomalous(120, HISTORY)).isFalse();
    assertThat(DemandAnomalyDetector.isAnomalous(250, HISTORY)).isFalse();
  }

  @Test
  void needsEnoughHistoryAndVolume() {
    assertThat(DemandAnomalyDetector.isAnomalous(500, null)).isFalse();
    assertThat(DemandAnomalyDetector.isAnomalous(500, new CustomerSkuStats(1, 2, 10, 12)))
        .isFalse();
    // 20 und es 10 veces el promedio, pero muy poco para retener un pedido
    assertThat(DemandAnomalyDetector.isAnomalous(20, new CustomerSkuStats(1, 30, 2, 4))).isFalse();
  }
}
