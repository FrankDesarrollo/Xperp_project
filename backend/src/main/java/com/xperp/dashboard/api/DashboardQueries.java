package com.xperp.dashboard.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** KPIs del tablero (AGENTS.md, sección 9.5). Solo lectura. */
public interface DashboardQueries {

  record CategoryDays(String categoryCode, String categoryName, double days) {}

  record SupplierFulfillment(
      String supplierCode, String supplierName, int orders, double onTimeRate) {}

  record Point(LocalDate date, double value) {}

  record KpiSnapshot(
      LocalDate from,
      LocalDate to,
      BigDecimal salesToday,
      BigDecimal salesPeriod,
      int ordersToday,
      BigDecimal grossMargin,
      double grossMarginPct,
      double serviceLevel,
      int lostSalesUnits,
      BigDecimal lostSalesAmount,
      int stockoutSkus,
      int lowStockSkus,
      int openPurchaseOrders,
      int latePurchaseOrders,
      BigDecimal purchasesPeriod,
      double budgetExecutionPct,
      BigDecimal inventoryValue,
      double rotation,
      Map<String, Integer> decisionsByStatus,
      Double avgApprovalMinutes,
      BigDecimal overcostAssumed,
      List<CategoryDays> inventoryDaysByCategory,
      List<SupplierFulfillment> supplierFulfillment) {}

  KpiSnapshot kpis(LocalDate from, LocalDate to, Long warehouseId);

  /** Presupuesto de una categoría en el mes y su proyección al cierre. */
  record BudgetLine(
      long categoryId,
      String categoryCode,
      String categoryName,
      BigDecimal amount,
      BigDecimal committed,
      BigDecimal spent,
      double usedPct,
      double projectedPct,
      BigDecimal projectedOverrun) {}

  /** Cifras financieras del periodo (ADR 0004). Las calcula el sistema con consultas, sin IA. */
  record FinanceSnapshot(
      LocalDate from,
      LocalDate to,
      int daysElapsed,
      int daysInPeriod,
      BigDecimal revenue,
      BigDecimal costOfSales,
      BigDecimal grossMargin,
      double grossMarginPct,
      BigDecimal freight,
      double freightPct,
      BigDecimal marginAfterFreight,
      BigDecimal purchasesReceived,
      BigDecimal purchasesCommitted,
      BigDecimal lostSalesAmount,
      BigDecimal payablesOpen,
      BigDecimal payablesDue7d,
      BigDecimal payablesOverdue,
      BigDecimal inventoryValue,
      BigDecimal inventoryExcessValue,
      BigDecimal slowMoverValue,
      List<BudgetLine> budgets) {}

  /**
   * Ingresos, costo, margen y fletes entre {@code from} y {@code to}; presupuesto, compras, cuentas
   * por pagar e inventario al día {@code to}.
   */
  FinanceSnapshot finance(LocalDate from, LocalDate to);

  List<Point> series(String metric, LocalDate from, LocalDate to, Long warehouseId);
}
