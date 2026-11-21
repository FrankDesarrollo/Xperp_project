package com.xperp.ai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Cifras de impacto que ve quien aprueba (ADR 0004). */
class ImpactTest {

  private static Map<String, Impact.Item> byLabel(List<Impact.Item> items) {
    return items.stream().collect(Collectors.toMap(Impact.Item::label, Function.identity()));
  }

  @Test
  void unitsAtRiskCoverLeadTimePlusOneReviewDay() {
    // 100 und/día, 2 días de entrega + 1 de revisión = 300; hay 120 + 30 en camino → faltan 150.
    assertThat(Impact.unitsAtRisk(100, 2, 120, 30)).isEqualTo(150);
    // Si alcanza, no hay riesgo; un disponible negativo cuenta como cero.
    assertThat(Impact.unitsAtRisk(100, 2, 400, 0)).isZero();
    assertThat(Impact.unitsAtRisk(10, 0, -5, 0)).isEqualTo(10);
    assertThat(Impact.unitsAtRisk(0, 3, 0, 0)).isZero();
  }

  @Test
  void purchaseShowsWhatItCommitsProtectsAndEarns() {
    // Aceite 1 L: 240 und a 7.800, se vende a 9.800; 120 und/día, entrega en 2 días, quedan 60.
    Impact.PurchaseLine line =
        new Impact.PurchaseLine(
            "ABA-007", 240, new BigDecimal("7800"), new BigDecimal("9800"), 120, 2, 60, 0);
    Map<String, Impact.Item> items =
        byLabel(
            Impact.purchase(
                List.of(line),
                List.of(
                    new Impact.BudgetUse(
                        "Abarrotes", new BigDecimal("1872000"), new BigDecimal("18720000")))));

    assertThat(items.get("Compromete").value()).isEqualTo("1872000.00");
    assertThat(items.get("Compromete").effect()).isEqualTo(Impact.COST);
    assertThat(items.get("Margen bruto esperado al venderlo").value()).isEqualTo("480000.00");
    // 120 × 3 − 60 = 300 und × 9.800
    assertThat(items.get("Ventas en riesgo si no se compra hoy").value()).isEqualTo("2940000.00");
    assertThat(items.get("Ventas en riesgo si no se compra hoy").effect()).isEqualTo(Impact.RISK);
    assertThat(items.get("Usa del disponible de Abarrotes").value()).isEqualTo("10.0");
    // Al llegar: (60 + 240 − 120 × 2) / 120 = 0,5 días
    assertThat(items.get("Cobertura al recibir").value()).isEqualTo("0.5");
  }

  @Test
  void purchaseWithoutBudgetLeftIsARisk() {
    Impact.PurchaseLine line =
        new Impact.PurchaseLine(
            "CPE-006", 8, new BigDecimal("32000"), new BigDecimal("41500"), 15, 3, 0, 0);
    Map<String, Impact.Item> items =
        byLabel(
            Impact.purchase(
                List.of(line),
                List.of(
                    new Impact.BudgetUse(
                        "Cuidado personal", new BigDecimal("256000"), BigDecimal.ZERO))));
    assertThat(items.get("Excede el presupuesto de Cuidado personal").effect())
        .isEqualTo(Impact.RISK);
  }

  @Test
  void reassignShowsOvercostOrSaving() {
    Map<String, Impact.Item> over =
        byLabel(Impact.reassign(new BigDecimal("1000000"), new BigDecimal("1100000"), 1));
    assertThat(over.get("Sobrecosto frente a la OC original").value()).isEqualTo("100000.00");
    assertThat(over.get("Sobrecosto frente a la OC original").note()).startsWith("10 %");
    assertThat(over.get("Entrega del nuevo proveedor").value()).isEqualTo("1.0");

    Map<String, Impact.Item> saving =
        byLabel(Impact.reassign(new BigDecimal("1000000"), new BigDecimal("950000"), null));
    assertThat(saving.get("Ahorro frente a la OC original").value()).isEqualTo("50000.00");
    assertThat(saving.get("Ahorro frente a la OC original").effect()).isEqualTo(Impact.BENEFIT);
    assertThat(saving).doesNotContainKey("Entrega del nuevo proveedor");
  }

  @Test
  void shipmentComparesWithTheCheapestCarrier() {
    Map<String, Impact.Item> items =
        byLabel(
            Impact.shipment(
                new BigDecimal("40000"),
                new BigDecimal("400000"),
                new BigDecimal("30000"),
                "TRP-01"));
    assertThat(items.get("Flete").value()).isEqualTo("40000.00");
    assertThat(items.get("Del valor del pedido").value()).isEqualTo("10.0");
    assertThat(items.get("Más que la opción más barata").value()).isEqualTo("10000.00");

    // Si la elegida ya es la más barata, no se muestra diferencia.
    assertThat(
            byLabel(
                Impact.shipment(
                    new BigDecimal("30000"),
                    new BigDecimal("400000"),
                    new BigDecimal("30000"),
                    "TRP-01")))
        .doesNotContainKey("Más que la opción más barata");
  }

  @Test
  void adjustmentSignDecidesCostOrBenefit() {
    Impact.Item loss = Impact.adjustment(-90, new BigDecimal("8400")).get(0);
    assertThat(loss.label()).isEqualTo("Pérdida de inventario a costo");
    assertThat(loss.value()).isEqualTo("756000.00");
    assertThat(loss.effect()).isEqualTo(Impact.COST);
    assertThat(Impact.adjustment(10, new BigDecimal("8400")).get(0).effect())
        .isEqualTo(Impact.BENEFIT);
  }

  @Test
  void transferAvoidsAPurchaseAndProtectsSales() {
    Map<String, Impact.Item> items =
        byLabel(
            Impact.transfer(24, new BigDecimal("32000"), new BigDecimal("41500"), 6, 0, 2, 30.5));
    assertThat(items.get("Compra que se evita").value()).isEqualTo("768000.00");
    // 6 und/día × (2 + 1) = 18 und × 41.500
    assertThat(items.get("Ventas en riesgo sin el traslado").value()).isEqualTo("747000.00");
  }
}
