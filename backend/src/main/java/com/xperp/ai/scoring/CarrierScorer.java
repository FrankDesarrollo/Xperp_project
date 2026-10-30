package com.xperp.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Puntaje de transportadoras (AGENTS.md, sección 8.3): {@code 0.5 × costo + 0.3 × tiempo + 0.2 ×
 * cumplimiento}, entre las que cubren la ciudad de destino. Función pura.
 */
public final class CarrierScorer {

  public record Candidate(
      long carrierId,
      String carrierCode,
      String carrierName,
      String service,
      double cost,
      int etaDays,
      double onTimeRate) {}

  public record Scored(Candidate candidate, double costScore, double timeScore, double total) {}

  private CarrierScorer() {}

  public static List<Scored> score(List<Candidate> candidates) {
    if (candidates.isEmpty()) {
      return List.of();
    }
    double minCost = candidates.stream().mapToDouble(Candidate::cost).min().orElse(0);
    double maxCost = candidates.stream().mapToDouble(Candidate::cost).max().orElse(0);
    int minEta = candidates.stream().mapToInt(Candidate::etaDays).min().orElse(0);
    int maxEta = candidates.stream().mapToInt(Candidate::etaDays).max().orElse(0);
    List<Scored> result = new ArrayList<>();
    for (Candidate c : candidates) {
      double cost = SupplierScorer.normalizeLowerIsBetter(c.cost(), minCost, maxCost);
      double time = SupplierScorer.normalizeLowerIsBetter(c.etaDays(), minEta, maxEta);
      double total = 0.5 * cost + 0.3 * time + 0.2 * c.onTimeRate();
      result.add(
          new Scored(
              c,
              SupplierScorer.round(cost),
              SupplierScorer.round(time),
              SupplierScorer.round(total)));
    }
    result.sort(Comparator.comparingDouble(Scored::total).reversed());
    return result;
  }

  public static boolean isTie(List<Scored> scored) {
    return scored.size() >= 2
        && scored.get(0).total() - scored.get(1).total() < SupplierScorer.TIE_THRESHOLD;
  }
}
