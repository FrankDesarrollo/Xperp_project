package com.xperp.ai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.xperp.ai.rules.StockAdvice.Kind;
import com.xperp.ai.rules.StockAdvice.Priority;
import org.junit.jupiter.api.Test;

class StockAdviceTest {

  // Existencia de referencia: punto de reorden 430, tope 1.200.
  private static Kind kind(
      int available, int inTransit, double cover, boolean openPo, boolean pending, boolean donor) {
    return StockAdvice.kind(available, inTransit, 430, 1200, cover, openPo, pending, donor);
  }

  @Test
  void aboveReorderPointAndBelowMaxNeedsNothing() {
    assertThat(kind(800, 0, 8, false, false, false)).isNull();
  }

  @Test
  void inTransitThatLiftsStockAboveReorderPointNeedsNothing() {
    assertThat(kind(300, 200, 3, false, false, false)).isNull();
  }

  @Test
  void belowReorderPointWithoutAlternativesRecommendsBuying() {
    assertThat(kind(360, 0, 3, false, false, false)).isEqualTo(Kind.BUY);
  }

  @Test
  void openPurchaseOrderMeansWaitingInsteadOfBuyingTwice() {
    assertThat(kind(100, 50, 1, true, false, true)).isEqualTo(Kind.INCOMING);
  }

  @Test
  void pendingDecisionWinsOverEverythingElse() {
    assertThat(kind(0, 0, 0, true, true, true)).isEqualTo(Kind.PENDING_APPROVAL);
  }

  @Test
  void urgentShortageWithDonorRecommendsTransfer() {
    assertThat(kind(0, 0, 0, false, false, true)).isEqualTo(Kind.TRANSFER);
    assertThat(kind(150, 0, 1.5, false, false, true)).isEqualTo(Kind.TRANSFER);
  }

  @Test
  void urgencyStartsBelowTwoDaysOfCoverLikeTheReplenishmentAgent() {
    assertThat(StockAdvice.urgent(150, 1.9)).isTrue();
    assertThat(StockAdvice.urgent(150, 2.0)).isFalse();
    assertThat(StockAdvice.urgent(0, 9)).isTrue();
  }

  @Test
  void donorIsIgnoredWhenNotUrgentOrWhenSomethingIsAlreadyInTransit() {
    assertThat(kind(360, 0, 3, false, false, true)).isEqualTo(Kind.BUY);
    assertThat(kind(0, 50, 0, false, false, true)).isEqualTo(Kind.BUY);
  }

  @Test
  void aboveMaxStockIsExcess() {
    assertThat(kind(1201, 0, 40, false, false, false)).isEqualTo(Kind.EXCESS);
    assertThat(kind(1200, 0, 30, false, false, false)).isNull();
  }

  @Test
  void withoutMaxStockThereIsNoExcess() {
    assertThat(StockAdvice.kind(5000, 0, 430, 0, 90, false, false, false)).isNull();
  }

  @Test
  void shortagesComeFirstEvenWhenAnOrderIsComing() {
    assertThat(StockAdvice.priority(Kind.BUY, 0, 0)).isEqualTo(Priority.HIGH);
    assertThat(StockAdvice.priority(Kind.INCOMING, 120, 1.2)).isEqualTo(Priority.HIGH);
  }

  @Test
  void replenishingWithoutUrgencyIsMediumAndWaitingOrExcessIsLow() {
    assertThat(StockAdvice.priority(Kind.BUY, 360, 3)).isEqualTo(Priority.MEDIUM);
    assertThat(StockAdvice.priority(Kind.PENDING_APPROVAL, 360, 3)).isEqualTo(Priority.MEDIUM);
    assertThat(StockAdvice.priority(Kind.INCOMING, 360, 3)).isEqualTo(Priority.LOW);
    assertThat(StockAdvice.priority(Kind.EXCESS, 0, 0)).isEqualTo(Priority.LOW);
  }
}
