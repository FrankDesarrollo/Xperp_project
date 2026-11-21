package com.xperp.ai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.xperp.ai.rules.FinanceRules.Finding;
import com.xperp.dashboard.api.DashboardQueries.BudgetLine;
import com.xperp.dashboard.api.DashboardQueries.FinanceSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hallazgos financieros (ADR 0004). Cada prueba parte de un mes sano y cambia una sola cifra, así
 * se ve que el hallazgo sale por su regla y no por otra.
 */
class FinanceRulesTest {

  private static BigDecimal m(String v) {
    return new BigDecimal(v);
  }

  private static BudgetLine budget(String code, double usedPct, double projectedPct) {
    BigDecimal amount = m("70000000");
    BigDecimal used = amount.multiply(BigDecimal.valueOf(usedPct / 100));
    BigDecimal projected = amount.multiply(BigDecimal.valueOf(projectedPct / 100));
    return new BudgetLine(
        1,
        code,
        "Cuidado personal",
        amount,
        used,
        BigDecimal.ZERO,
        usedPct,
        projectedPct,
        projected.subtract(amount).max(BigDecimal.ZERO));
  }

  /** Mes sano: margen 22 %, fletes 5 %, sin vencidos, sin exceso, presupuesto al 50 %. */
  private static FinanceSnapshot healthy(List<BudgetLine> budgets) {
    return new FinanceSnapshot(
        LocalDate.of(2026, 11, 1),
        LocalDate.of(2026, 11, 15),
        15,
        30,
        m("100000000"),
        m("78000000"),
        m("22000000"),
        22,
        m("5000000"),
        5,
        m("17000000"),
        m("60000000"),
        m("10000000"),
        m("500000"),
        m("0"),
        m("0"),
        m("0"),
        m("200000000"),
        m("1000000"),
        m("2000000"),
        budgets);
  }

  private static FinanceSnapshot healthy() {
    return healthy(List.of(budget("CPE", 50, 95)));
  }

  private static List<String> codes(FinanceSnapshot s) {
    return FinanceRules.findings(s).stream().map(Finding::code).toList();
  }

  @Test
  void aHealthyMonthHasNoFindings() {
    assertThat(FinanceRules.findings(healthy())).isEmpty();
    assertThat(FinanceRules.facts(healthy(), List.of())).contains("Sin hallazgos");
  }

  @Test
  void projectedOverrunIsMediumAndSevereAboveOneHundredTen() {
    Finding medium = FinanceRules.findings(healthy(List.of(budget("CPE", 60, 105)))).get(0);
    assertThat(medium.code()).isEqualTo("budget_overrun_projected");
    assertThat(medium.severity()).isEqualTo(FinanceRules.MEDIUM);
    assertThat(medium.impact()).isPositive();
    assertThat(medium.key()).isEqualTo("budget_overrun_projected:CPE");

    Finding severe = FinanceRules.findings(healthy(List.of(budget("CPE", 60, 112)))).get(0);
    assertThat(severe.severity()).isEqualTo(FinanceRules.HIGH);

    // Ya gastado el 100 %: grave aunque la proyección sea moderada.
    Finding spent = FinanceRules.findings(healthy(List.of(budget("CPE", 101, 104)))).get(0);
    assertThat(spent.severity()).isEqualTo(FinanceRules.HIGH);
  }

  @Test
  void nearTheLimitWithoutProjectedOverrunIsAWarning() {
    assertThat(codes(healthy(List.of(budget("CPE", 86, 99))))).containsExactly("budget_near_limit");
    assertThat(codes(healthy(List.of(budget("CPE", 84, 99))))).isEmpty();
  }

  @Test
  void overduePayablesAreSevereAndDueSoonIsLow() {
    FinanceSnapshot h = healthy();
    FinanceSnapshot s =
        new FinanceSnapshot(
            h.from(),
            h.to(),
            h.daysElapsed(),
            h.daysInPeriod(),
            h.revenue(),
            h.costOfSales(),
            h.grossMargin(),
            h.grossMarginPct(),
            h.freight(),
            h.freightPct(),
            h.marginAfterFreight(),
            h.purchasesReceived(),
            h.purchasesCommitted(),
            h.lostSalesAmount(),
            m("9000000"),
            m("3000000"),
            m("1000000"),
            h.inventoryValue(),
            h.inventoryExcessValue(),
            h.slowMoverValue(),
            h.budgets());
    List<Finding> f = FinanceRules.findings(s);
    assertThat(f)
        .extracting(Finding::code)
        .containsExactly("payables_overdue", "payables_due_soon");
    assertThat(f.get(0).severity()).isEqualTo(FinanceRules.HIGH);
    assertThat(f.get(1).severity()).isEqualTo(FinanceRules.LOW);
  }

  @Test
  void tiedUpCapitalAboveThreePercentIsReported() {
    FinanceSnapshot h = healthy();
    FinanceSnapshot s =
        new FinanceSnapshot(
            h.from(),
            h.to(),
            h.daysElapsed(),
            h.daysInPeriod(),
            h.revenue(),
            h.costOfSales(),
            h.grossMargin(),
            h.grossMarginPct(),
            h.freight(),
            h.freightPct(),
            h.marginAfterFreight(),
            h.purchasesReceived(),
            h.purchasesCommitted(),
            h.lostSalesAmount(),
            h.payablesOpen(),
            h.payablesDue7d(),
            h.payablesOverdue(),
            m("200000000"),
            m("2000000"),
            m("8000000"),
            h.budgets());
    Finding f = FinanceRules.findings(s).get(0);
    assertThat(f.code()).isEqualTo("excess_inventory");
    assertThat(f.impact()).isEqualByComparingTo("8000000");
  }

  @Test
  void freightLostSalesAndMarginHaveTheirOwnThresholds() {
    FinanceSnapshot h = healthy();
    FinanceSnapshot s =
        new FinanceSnapshot(
            h.from(),
            h.to(),
            h.daysElapsed(),
            h.daysInPeriod(),
            m("100000000"),
            m("85000000"),
            m("15000000"),
            15,
            m("9000000"),
            9,
            m("6000000"),
            h.purchasesReceived(),
            h.purchasesCommitted(),
            m("6000000"),
            h.payablesOpen(),
            h.payablesDue7d(),
            h.payablesOverdue(),
            h.inventoryValue(),
            h.inventoryExcessValue(),
            h.slowMoverValue(),
            h.budgets());
    List<Finding> f = FinanceRules.findings(s);
    assertThat(f)
        .extracting(Finding::code)
        .containsExactly("lost_sales_high", "freight_high", "margin_low");
    // Ventas perdidas del 6 %: grave, y por eso va primero.
    assertThat(f.get(0).severity()).isEqualTo(FinanceRules.HIGH);
  }

  @Test
  void factsCiteTheNumbersAndTheFindings() {
    String facts =
        FinanceRules.facts(
            healthy(List.of(budget("CPE", 60, 112))),
            FinanceRules.findings(healthy(List.of(budget("CPE", 60, 112)))));
    assertThat(facts).contains("$ 100.000.000").contains("22 %").contains("Cuidado personal");
  }
}
