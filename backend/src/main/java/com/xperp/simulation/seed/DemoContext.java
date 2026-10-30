package com.xperp.simulation.seed;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.sales.api.CustomerDto;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.time.HolidayCalendar;
import com.xperp.simulation.demand.DemandProfile;
import com.xperp.simulation.demand.OrderPlanner;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lo que el planificador de pedidos necesita de la empresa demo: productos con su demanda base,
 * clientes por bodega, segmentos, factores y festivos. Se arma una vez y se reutiliza.
 */
@Component
public class DemoContext implements ResettableCache {

  public record WarehouseDemand(
      WarehouseDto warehouse, double factor, List<OrderPlanner.CustomerSpec> customers) {}

  public record Snapshot(
      List<OrderPlanner.ProductSpec> products,
      List<WarehouseDemand> warehouses,
      Map<String, OrderPlanner.SegmentSpec> segments,
      DemandProfile profile,
      Set<LocalDate> holidays) {}

  private final DemoFiles files;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final SalesService sales;
  private final HolidayCalendar holidays;
  private volatile Snapshot snapshot;

  DemoContext(
      DemoFiles files,
      CatalogService catalog,
      InventoryService inventory,
      SalesService sales,
      HolidayCalendar holidays) {
    this.files = files;
    this.catalog = catalog;
    this.inventory = inventory;
    this.sales = sales;
    this.holidays = holidays;
  }

  public Snapshot snapshot() {
    Snapshot local = snapshot;
    if (local == null) {
      local = build();
      snapshot = local;
    }
    return local;
  }

  private Snapshot build() {
    Map<String, Double> base = files.baseDemand();
    List<OrderPlanner.ProductSpec> products = new ArrayList<>();
    for (ProductDto p : catalog.activeProducts()) {
      products.add(
          new OrderPlanner.ProductSpec(
              p.id(),
              p.sku(),
              p.categoryCode(),
              p.unitsPerCase(),
              base.getOrDefault(p.sku(), 0.0)));
    }
    Map<String, Double> factors = files.warehouseFactors();
    List<CustomerDto> customers = sales.activeCustomers();
    List<WarehouseDemand> warehouses = new ArrayList<>();
    for (WarehouseDto w : inventory.warehouses()) {
      List<OrderPlanner.CustomerSpec> specs =
          customers.stream()
              .filter(c -> c.warehouseId() == w.id())
              .map(c -> new OrderPlanner.CustomerSpec(c.id(), c.segmentCode()))
              .toList();
      warehouses.add(new WarehouseDemand(w, factors.getOrDefault(w.code(), 1.0), specs));
    }
    return new Snapshot(
        products,
        warehouses,
        files.segments(),
        files.profile(),
        Set.copyOf(holidays.holidays().keySet()));
  }

  public List<OrderPlanner.PlannedOrder> plan(LocalDate date, WarehouseDemand w, long seed) {
    Snapshot s = snapshot();
    return OrderPlanner.plan(
        date,
        w.warehouse().code(),
        w.factor(),
        s.products(),
        w.customers(),
        s.segments(),
        s.profile(),
        s.holidays(),
        seed);
  }

  @Override
  public void clearCache() {
    snapshot = null;
  }
}
