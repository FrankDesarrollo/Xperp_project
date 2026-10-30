package com.xperp.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Puntaje de proveedores (AGENTS.md, sección 8.3): {@code 0.40 × precio + 0.25 × tiempo + 0.25 ×
 * cumplimiento + 0.10 × preferido}. Precio y tiempo se normalizan entre las ofertas del mismo SKU
 * (menor es mejor). Función pura.
 */
public final class SupplierScorer {

  /** Diferencia por debajo de la cual hay empate técnico y decide el LLM. */
  public static final double TIE_THRESHOLD = 0.05;

  public record Weights(double price, double leadTime, double fulfillment, double preferred) {
    public static final Weights DEFAULT = new Weights(0.40, 0.25, 0.25, 0.10);
  }

  public record Candidate(
      long supplierId,
      String supplierCode,
      String supplierName,
      double unitCost,
      int leadTimeDays,
      double fulfillment,
      boolean preferred) {}

  public record Scored(
      Candidate candidate, double priceScore, double leadTimeScore, double total) {}

  private SupplierScorer() {}

  /** Califica y ordena de mayor a menor puntaje. */
  public static List<Scored> score(List<Candidate> candidates, Weights w) {
    if (candidates.isEmpty()) {
      return List.of();
    }
    double minCost = candidates.stream().mapToDouble(Candidate::unitCost).min().orElse(0);
    double maxCost = candidates.stream().mapToDouble(Candidate::unitCost).max().orElse(0);
    int minLead = candidates.stream().mapToInt(Candidate::leadTimeDays).min().orElse(0);
    int maxLead = candidates.stream().mapToInt(Candidate::leadTimeDays).max().orElse(0);
    List<Scored> result = new ArrayList<>();
    for (Candidate c : candidates) {
      double price = normalizeLowerIsBetter(c.unitCost(), minCost, maxCost);
      double lead = normalizeLowerIsBetter(c.leadTimeDays(), minLead, maxLead);
      double total =
          w.price() * price
              + w.leadTime() * lead
              + w.fulfillment() * c.fulfillment()
              + w.preferred() * (c.preferred() ? 1 : 0);
      result.add(new Scored(c, round(price), round(lead), round(total)));
    }
    result.sort(Comparator.comparingDouble(Scored::total).reversed());
    return result;
  }

  /** ¿Las dos mejores ofertas están en empate técnico? */
  public static boolean isTie(List<Scored> scored) {
    return scored.size() >= 2 && scored.get(0).total() - scored.get(1).total() < TIE_THRESHOLD;
  }

  static double normalizeLowerIsBetter(double value, double min, double max) {
    if (max - min < 1e-9) {
      return 1;
    }
    return (max - value) / (max - min);
  }

  static double round(double v) {
    return Math.round(v * 1000) / 1000.0;
  }
}
