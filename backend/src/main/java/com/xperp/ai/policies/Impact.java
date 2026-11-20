package com.xperp.ai.policies;

import com.xperp.shared.persistence.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Impacto económico y operativo de una recomendación de la IA (ADR 0004). La persona que aprueba ve
 * qué cuesta, qué cambia en la operación y qué pasa si la rechaza. Se guarda en {@code
 * ai_decisions.impact}.
 *
 * @param amount monto que compromete o libera la acción (positivo compromete, negativo libera)
 * @param economic efecto económico en una frase, con los números
 * @param operational efecto operativo en una frase (cobertura, quiebres, pedidos)
 * @param riskIfRejected qué pasa si nadie la aprueba
 * @param riskLevel {@code low}, {@code medium} o {@code high}
 * @param budget efecto en el presupuesto de cada categoría; lo completa {@link ActionGateway}
 */
public record Impact(
    BigDecimal amount,
    String economic,
    String operational,
    String riskIfRejected,
    String riskLevel,
    List<Map<String, Object>> budget) {

  public static final String LOW = "low";
  public static final String MEDIUM = "medium";
  public static final String HIGH = "high";

  public Impact {
    budget = budget == null ? List.of() : List.copyOf(budget);
  }

  public static Impact of(
      BigDecimal amount,
      String economic,
      String operational,
      String riskIfRejected,
      String riskLevel) {
    return new Impact(amount, economic, operational, riskIfRejected, riskLevel, List.of());
  }

  /** Impacto mínimo cuando quien propone no lo calculó: solo el monto. */
  static Impact fromAmount(BigDecimal amount) {
    return new Impact(
        amount,
        amount == null ? null : "Compromete " + Money.display(amount),
        null,
        null,
        null,
        List.of());
  }

  /** Lee un impacto guardado con {@link #toMap()}; null si el mapa está vacío. */
  public static Impact fromMap(Map<String, Object> m) {
    if (m == null || m.isEmpty()) {
      return null;
    }
    Object amount = m.get("amount");
    return new Impact(
        amount == null ? null : new BigDecimal(amount.toString()),
        str(m.get("economic")),
        str(m.get("operational")),
        str(m.get("risk_if_rejected")),
        str(m.get("risk_level")),
        List.of());
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  Impact withBudget(List<Map<String, Object>> lines) {
    List<Map<String, Object>> all = new ArrayList<>(budget);
    all.addAll(lines);
    return new Impact(amount, economic, operational, riskIfRejected, riskLevel, all);
  }

  /** Riesgo según los días que aguanta el inventario frente al tiempo de reposición. */
  public static String riskFromCover(double daysOfCover, double leadTimeDays) {
    if (daysOfCover <= leadTimeDays) {
      return HIGH;
    }
    if (daysOfCover <= leadTimeDays + 3) {
      return MEDIUM;
    }
    return LOW;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("amount", amount == null ? null : Money.format(amount));
    m.put("economic", economic);
    m.put("operational", operational);
    m.put("risk_if_rejected", riskIfRejected);
    m.put("risk_level", riskLevel);
    m.put("budget", budget);
    return m;
  }
}
