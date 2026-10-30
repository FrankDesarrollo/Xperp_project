package com.xperp.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringTest {

  /** Arroz en CAL-01 (AGENTS.md, 2.6): PRV-01 preferido gana a PRV-02 y PRV-09. */
  @Test
  void preferredLocalSupplierWinsRiceInCali() {
    List<SupplierScorer.Scored> scored =
        SupplierScorer.score(
            List.of(
                new SupplierScorer.Candidate(1, "PRV-01", "Granos", 3600, 2, 0.95, true),
                new SupplierScorer.Candidate(2, "PRV-02", "Molinera", 3420, 4, 0.85, false),
                new SupplierScorer.Candidate(9, "PRV-09", "Mayorista", 3960, 1, 0.98, false)),
            SupplierScorer.Weights.DEFAULT);
    assertThat(scored.get(0).candidate().supplierCode()).isEqualTo("PRV-01");
    assertThat(scored.get(0).total())
        .isGreaterThan(scored.get(1).total() + SupplierScorer.TIE_THRESHOLD);
    assertThat(SupplierScorer.isTie(scored)).isFalse();
  }

  /** En BOG-01, Molinera Andina (Bogotá, 1 día) gana el arroz. */
  @Test
  void bogotaSupplierWinsRiceInBogota() {
    List<SupplierScorer.Scored> scored =
        SupplierScorer.score(
            List.of(
                new SupplierScorer.Candidate(1, "PRV-01", "Granos", 3600, 4, 0.95, true),
                new SupplierScorer.Candidate(2, "PRV-02", "Molinera", 3420, 1, 0.85, false),
                new SupplierScorer.Candidate(9, "PRV-09", "Mayorista", 3960, 3, 0.98, false)),
            SupplierScorer.Weights.DEFAULT);
    assertThat(scored.get(0).candidate().supplierCode()).isEqualTo("PRV-02");
  }

  @Test
  void normalizationGivesBestOneAndWorstZero() {
    assertThat(SupplierScorer.normalizeLowerIsBetter(10, 10, 20)).isEqualTo(1.0);
    assertThat(SupplierScorer.normalizeLowerIsBetter(20, 10, 20)).isEqualTo(0.0);
    assertThat(SupplierScorer.normalizeLowerIsBetter(5, 5, 5)).isEqualTo(1.0);
  }

  @Test
  void tieWhenTopTwoAreCloserThanThreshold() {
    List<SupplierScorer.Scored> scored =
        SupplierScorer.score(
            List.of(
                new SupplierScorer.Candidate(1, "A", "A", 100, 2, 0.90, false),
                new SupplierScorer.Candidate(2, "B", "B", 100, 2, 0.91, false)),
            SupplierScorer.Weights.DEFAULT);
    assertThat(SupplierScorer.isTie(scored)).isTrue();
  }

  @Test
  void carrierScoreFavorsCheapAndFastLocalCarrier() {
    List<CarrierScorer.Scored> scored =
        CarrierScorer.score(
            List.of(
                new CarrierScorer.Candidate(
                    1, "TRP-01", "Rápido Valle", "Estándar", 22500, 1, 0.95),
                new CarrierScorer.Candidate(2, "TRP-02", "Cordillera", "Estándar", 31000, 3, 0.88),
                new CarrierScorer.Candidate(3, "TRP-03", "TransCarga", "Express", 37200, 1, 0.96)));
    assertThat(scored.get(0).candidate().carrierCode()).isEqualTo("TRP-01");
    assertThat(CarrierScorer.isTie(scored)).isFalse();
  }

  @Test
  void emptyInputGivesEmptyScore() {
    assertThat(SupplierScorer.score(List.of(), SupplierScorer.Weights.DEFAULT)).isEmpty();
    assertThat(CarrierScorer.score(List.of())).isEmpty();
  }
}
