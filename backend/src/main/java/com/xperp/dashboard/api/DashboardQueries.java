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

  List<Point> series(String metric, LocalDate from, LocalDate to, Long warehouseId);
}
