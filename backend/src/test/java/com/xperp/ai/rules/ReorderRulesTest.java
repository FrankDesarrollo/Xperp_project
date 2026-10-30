package com.xperp.ai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReorderRulesTest {

  @Test
  void demandStatsUsesSampleStandardDeviation() {
    ReorderRules.DemandStats s = ReorderRules.DemandStats.of(List.of(10, 12, 8, 10));
    assertThat(s.mean()).isEqualTo(10.0);
    assertThat(s.std()).isCloseTo(1.633, org.assertj.core.data.Offset.offset(0.001));
    assertThat(s.days()).isEqualTo(4);
  }

  @Test
  void emptySeriesHasZeroStats() {
    assertThat(ReorderRules.DemandStats.of(List.of()).mean()).isZero();
  }

  @Test
  void safetyStockIsZTimesSigmaTimesSqrtLeadTime() {
    // 1,65 × 30 × √4 = 99
    assertThat(ReorderRules.safetyStock(ReorderRules.Z_95, 30, 4)).isEqualTo(99);
    assertThat(ReorderRules.safetyStock(ReorderRules.Z_95, 0, 4)).isZero();
  }

  @Test
  void reorderPointCoversLeadTimePlusReviewDay() {
    // 120 × (2 + 1) + 70
    assertThat(ReorderRules.reorderPoint(120, 2, 70)).isEqualTo(430);
  }

  @Test
  void needsReorderWhenAvailablePlusInTransitAtOrBelowReorderPoint() {
    assertThat(ReorderRules.needsReorder(360, 0, 430)).isTrue();
    assertThat(ReorderRules.needsReorder(360, 100, 430)).isFalse();
    assertThat(ReorderRules.needsReorder(0, 0, 0)).isFalse();
  }

  @Test
  void orderQuantityRoundsUpToCases() {
    // 1530 + 70 − 360 − 0 = 1240 → 104 cajas de 12 = 1248
    int qty = ReorderRules.orderQuantity(1530, 70, 360, 0, 12, 5, 0);
    assertThat(qty).isEqualTo(1248);
    assertThat(qty % 12).isZero();
  }

  @Test
  void orderQuantityRespectsMinimumCases() {
    assertThat(ReorderRules.orderQuantity(20, 0, 0, 0, 24, 5, 0)).isEqualTo(120);
  }

  @Test
  void orderQuantityIsCappedByMaxStock() {
    // Hacen falta 3000, pero el tope deja espacio para 1000 − 200 = 800 → 66 cajas
    assertThat(ReorderRules.orderQuantity(3000, 0, 200, 0, 12, 5, 1000)).isEqualTo(792);
  }

  @Test
  void noOrderWhenCovered() {
    assertThat(ReorderRules.orderQuantity(100, 10, 500, 0, 12, 5, 0)).isZero();
  }

  @Test
  void perishablesUseShorterCover() {
    assertThat(ReorderRules.coverDays(true)).isEqualTo(5);
    assertThat(ReorderRules.coverDays(false)).isEqualTo(10);
  }
}
