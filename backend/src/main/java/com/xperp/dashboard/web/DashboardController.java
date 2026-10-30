package com.xperp.dashboard.web;

import com.xperp.dashboard.api.DashboardQueries;
import com.xperp.shared.contract.api.DashboardApi;
import com.xperp.shared.contract.model.CategoryDays;
import com.xperp.shared.contract.model.DecisionCounts;
import com.xperp.shared.contract.model.Kpis;
import com.xperp.shared.contract.model.Series;
import com.xperp.shared.contract.model.SeriesPoint;
import com.xperp.shared.contract.model.SupplierFulfillment;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardController implements DashboardApi {

  private final DashboardQueries dashboard;
  private final Clock clock;

  DashboardController(DashboardQueries dashboard, Clock clock) {
    this.dashboard = dashboard;
    this.clock = clock;
  }

  @Override
  public ResponseEntity<Kpis> getKpis(LocalDate from, LocalDate to, Long warehouseId) {
    LocalDate today = SimTime.today(clock);
    LocalDate f = from == null ? today.withDayOfMonth(1) : from;
    LocalDate t = to == null ? today : to;
    DashboardQueries.KpiSnapshot k = dashboard.kpis(f, t, warehouseId);
    Map<String, Integer> d = k.decisionsByStatus();
    return ResponseEntity.ok(
        new Kpis(
            k.from(),
            k.to(),
            Money.format(k.salesToday()),
            Money.format(k.salesPeriod()),
            k.ordersToday(),
            Money.format(k.grossMargin()),
            k.grossMarginPct(),
            k.serviceLevel(),
            k.lostSalesUnits(),
            Money.format(k.lostSalesAmount()),
            k.stockoutSkus(),
            k.lowStockSkus(),
            k.openPurchaseOrders(),
            k.latePurchaseOrders(),
            Money.format(k.purchasesPeriod()),
            k.budgetExecutionPct(),
            Money.format(k.inventoryValue()),
            k.rotation(),
            new DecisionCounts(
                d.getOrDefault("executed", 0),
                d.getOrDefault("pending_approval", 0),
                d.getOrDefault("suggested", 0),
                d.getOrDefault("blocked", 0),
                d.getOrDefault("approved", 0),
                d.getOrDefault("rejected", 0)),
            k.avgApprovalMinutes(),
            Money.format(k.overcostAssumed()),
            k.inventoryDaysByCategory().stream()
                .map(c -> new CategoryDays(c.categoryCode(), c.categoryName(), c.days()))
                .toList(),
            k.supplierFulfillment().stream()
                .map(
                    s ->
                        new SupplierFulfillment(
                            s.supplierCode(), s.supplierName(), s.orders(), s.onTimeRate()))
                .toList()));
  }

  @Override
  public ResponseEntity<Series> getSeries(
      String metric, LocalDate from, LocalDate to, Long warehouseId) {
    LocalDate today = SimTime.today(clock);
    LocalDate t = to == null ? today : to;
    LocalDate f = from == null ? t.minusDays(29) : from;
    try {
      return ResponseEntity.ok(
          new Series(
              metric,
              dashboard.series(metric, f, t, warehouseId).stream()
                  .map(p -> new SeriesPoint(p.date(), p.value()))
                  .toList()));
    } catch (IllegalArgumentException e) {
      throw BusinessException.invalid("invalid_metric", e.getMessage());
    }
  }
}
