package com.xperp.simulation.scenarios;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockDto;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.partners.api.PartnersAdminGateway;
import com.xperp.purchasing.api.CreatePurchaseOrder;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.PurchaseLine;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.sales.api.CreateSalesOrder;
import com.xperp.sales.api.CustomerDto;
import com.xperp.sales.api.CustomerSkuStats;
import com.xperp.sales.api.SalesLine;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.config.SimulationSettings;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.time.SimTime;
import com.xperp.simulation.seed.DemoFiles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el guion de {@code demo/scenarios.yml} (AGENTS.md, 2.13 y 10.3). Los eventos de socios
 * externos (rechazo, retraso, alza de precio) se le envían a partners-mock al iniciar.
 */
@Component
public class ScenarioRunner {

  private static final Logger log = LoggerFactory.getLogger(ScenarioRunner.class);

  /** Escenario del guion con su hora absoluta. */
  public record Scenario(
      int index,
      int day,
      String time,
      String type,
      String title,
      String expected,
      Map<String, Object> params,
      Instant at) {}

  private final DemoFiles files;
  private final SimulationSettings settings;
  private final SalesService sales;
  private final PurchasingService purchasing;
  private final InventoryService inventory;
  private final CatalogService catalog;
  private final FinanceService finance;
  private final UserDirectory users;
  private final PartnersAdminGateway partners;
  private final Clock clock;
  private volatile List<Scenario> scenarios;

  ScenarioRunner(
      DemoFiles files,
      SimulationSettings settings,
      SalesService sales,
      PurchasingService purchasing,
      InventoryService inventory,
      CatalogService catalog,
      FinanceService finance,
      UserDirectory users,
      PartnersAdminGateway partners,
      Clock clock) {
    this.files = files;
    this.settings = settings;
    this.sales = sales;
    this.purchasing = purchasing;
    this.inventory = inventory;
    this.catalog = catalog;
    this.finance = finance;
    this.users = users;
    this.partners = partners;
    this.clock = clock;
  }

  /** Escenarios ordenados por hora. */
  @SuppressWarnings("unchecked")
  public List<Scenario> scenarios() {
    List<Scenario> local = scenarios;
    if (local == null) {
      List<Scenario> list = new ArrayList<>();
      int i = 0;
      for (Map<String, Object> s :
          (List<Map<String, Object>>) files.yaml("scenarios.yml").get("scenarios")) {
        int day = ((Number) s.get("day")).intValue();
        String time = String.valueOf(s.get("time"));
        list.add(
            new Scenario(
                i++,
                day,
                time,
                (String) s.get("type"),
                (String) s.get("title"),
                (String) s.get("expected"),
                (Map<String, Object>) s.getOrDefault("params", Map.of()),
                at(day, time)));
      }
      list.sort(Comparator.comparing(Scenario::at).thenComparing(Scenario::index));
      local = List.copyOf(list);
      scenarios = local;
    }
    return local;
  }

  Instant at(int day, String time) {
    LocalDate date = settings.startDate().plusDays(day - 1L);
    return date.atTime(LocalTime.parse(time)).atZone(SimTime.ZONE).toInstant();
  }

  /** Ejecuta los escenarios pendientes con hora ≤ {@code until}. Devuelve el nuevo cursor. */
  public int runDue(int cursor, Instant until) {
    List<Scenario> list = scenarios();
    while (cursor < list.size() && !list.get(cursor).at().isAfter(until)) {
      Scenario s = list.get(cursor);
      try {
        execute(s);
        log.info("Escenario día {} {}: {}", s.day(), s.time(), s.title());
      } catch (RuntimeException e) {
        log.warn("Falló el escenario '{}': {}", s.title(), e.getMessage());
      }
      cursor++;
    }
    return cursor;
  }

  private void execute(Scenario s) {
    switch (s.type()) {
      case "manual_po" -> manualPurchaseOrder(s.params());
      case "inventory_count" -> inventoryCount(s.params());
      case "anomalous_order" -> anomalousOrder(s.params());
      case "budget_pressure" -> budgetPressure(s.params());
      default -> {}
    }
  }

  /** Envía a partners-mock los eventos forzados del guion, con hora absoluta. */
  @SuppressWarnings("unchecked")
  public void configurePartners() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> p :
        (List<Map<String, Object>>) files.yaml("scenarios.yml").get("partners")) {
      Map<String, Object> m = new LinkedHashMap<>(p);
      Object day = p.containsKey("day") ? p.get("day") : p.get("from_day");
      String time = String.valueOf(p.getOrDefault("time", "00:00"));
      m.put("at", at(((Number) day).intValue(), time).toString());
      if ("price_change".equals(p.get("type"))) {
        ProductDto product = catalog.productBySku((String) p.get("sku")).orElseThrow();
        SupplierDto supplier = purchasing.supplierByCode((String) p.get("supplier")).orElseThrow();
        BigDecimal current =
            purchasing.offersForSupplier(supplier.id()).stream()
                .filter(o -> o.productId() == product.id())
                .map(OfferDto::unitCost)
                .findFirst()
                .orElse(product.cost());
        double pct = ((Number) p.get("increase_pct")).doubleValue();
        BigDecimal next = Money.roundToTens(current.multiply(BigDecimal.valueOf(1 + pct / 100)));
        m.put("unit_cost", Money.format(next));
      }
      out.add(m);
    }
    partners.configureScenarios(out);
  }

  private long buyer() {
    return users.byRole("purchasing").stream()
        .findFirst()
        .map(UserDirectory.UserRecord::id)
        .orElse(0L);
  }

  private void manualPurchaseOrder(Map<String, Object> p) {
    SupplierDto supplier = purchasing.supplierByCode((String) p.get("supplier")).orElseThrow();
    WarehouseDto warehouse = inventory.warehouseByCode((String) p.get("warehouse")).orElseThrow();
    ProductDto product = catalog.productBySku((String) p.get("sku")).orElseThrow();
    StockDto stock = inventory.stock(product.id(), warehouse.id());
    int cover = ((Number) p.getOrDefault("cover_days", 10)).intValue();
    int qty =
        Math.max(
            product.unitsPerCase() * 5,
            product.roundUpToCases((int) Math.ceil(stock.avgDailyDemand().doubleValue() * cover)));
    PurchaseOrderDto po =
        purchasing.create(
            new CreatePurchaseOrder(
                supplier.id(),
                warehouse.id(),
                List.of(new PurchaseLine(product.id(), qty, null)),
                buyer(),
                null,
                null,
                null,
                false,
                "OC manual de " + users.nameOf(buyer())));
    purchasing.send(po.id());
  }

  private void inventoryCount(Map<String, Object> p) {
    WarehouseDto warehouse = inventory.warehouseByCode((String) p.get("warehouse")).orElseThrow();
    ProductDto product = catalog.productBySku((String) p.get("sku")).orElseThrow();
    StockDto stock = inventory.stock(product.id(), warehouse.id());
    double ratio = ((Number) p.get("ratio")).doubleValue();
    int counted = (int) Math.round(stock.onHand() * ratio);
    inventory.recordCount(product.id(), warehouse.id(), counted, false);
  }

  private void anomalousOrder(Map<String, Object> p) {
    WarehouseDto warehouse = inventory.warehouseByCode((String) p.get("warehouse")).orElseThrow();
    ProductDto product = catalog.productBySku((String) p.get("sku")).orElseThrow();
    String segment = (String) p.get("segment");
    double factor = ((Number) p.get("factor")).doubleValue();
    CustomerDto best = null;
    CustomerSkuStats bestStats = null;
    for (CustomerDto c : sales.activeCustomers()) {
      if (!segment.equals(c.segmentCode()) || c.warehouseId() != warehouse.id()) {
        continue;
      }
      CustomerSkuStats stats =
          sales.customerHistory(c.id(), List.of(product.id()), null).get(product.id());
      if (stats != null && (bestStats == null || stats.lines() > bestStats.lines())) {
        best = c;
        bestStats = stats;
      }
    }
    if (best == null) {
      log.warn("No hay un cliente {} con historia de {}", segment, product.sku());
      return;
    }
    int qty = product.roundUpToCases((int) Math.ceil(bestStats.avgQuantity() * factor));
    sales.create(new CreateSalesOrder(best.id(), List.of(new SalesLine(product.id(), qty))));
  }

  @SuppressWarnings("unchecked")
  private void budgetPressure(Map<String, Object> p) {
    var category = catalog.categoryByCode((String) p.get("category")).orElseThrow();
    String period = SimTime.period(clock.instant());
    BudgetDto budget = finance.budget(category.id(), period);
    double target = ((Number) p.get("target_pct")).doubleValue() / 100.0;
    BigDecimal needed =
        budget.amount().multiply(BigDecimal.valueOf(target)).subtract(budget.used());
    if (needed.signum() <= 0) {
      log.info(
          "{} ya supera el {} %; no hace falta la compra de temporada",
          category.code(), target * 100);
      return;
    }
    SupplierDto supplier = purchasing.supplierByCode((String) p.get("supplier")).orElseThrow();
    WarehouseDto warehouse = inventory.warehouseByCode((String) p.get("warehouse")).orElseThrow();
    List<String> skus = (List<String>) p.get("skus");
    BigDecimal share = needed.divide(BigDecimal.valueOf(skus.size()), 2, RoundingMode.HALF_UP);
    List<PurchaseLine> lines = new ArrayList<>();
    for (String sku : skus) {
      ProductDto product = catalog.productBySku(sku).orElseThrow();
      BigDecimal cost =
          purchasing.offersForSupplier(supplier.id()).stream()
              .filter(o -> o.productId() == product.id())
              .map(OfferDto::unitCost)
              .findFirst()
              .orElse(product.cost());
      int units = share.divide(cost, 0, RoundingMode.CEILING).intValue();
      lines.add(new PurchaseLine(product.id(), product.roundUpToCases(units), null));
    }
    PurchaseOrderDto po =
        purchasing.create(
            new CreatePurchaseOrder(
                supplier.id(),
                warehouse.id(),
                lines,
                buyer(),
                null,
                null,
                null,
                false,
                "Compra de temporada navideña de cuidado personal"));
    purchasing.send(po.id());
  }
}
