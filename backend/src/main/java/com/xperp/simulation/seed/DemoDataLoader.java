package com.xperp.simulation.seed;

import com.xperp.ai.api.AiFacade;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.CategoryDto;
import com.xperp.catalog.api.ProductCommand;
import com.xperp.catalog.api.ProductDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.logistics.api.CarrierCommand;
import com.xperp.logistics.api.LogisticsService;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierCommand;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.sales.api.CustomerCommand;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.config.SimulationSettings;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.tenancy.TenantDirectory;
import com.xperp.shared.time.HolidayCalendar;
import com.xperp.shared.time.SimClock;
import com.xperp.shared.time.SimTime;
import com.xperp.simulation.clock.SimulationStateStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Carga la empresa demo cuando la base está vacía (AGENTS.md, 2.14):
 *
 * <ol>
 *   <li>Empresa, usuarios, catálogo, proveedores, ofertas, transportadoras, clientes y
 *       presupuestos, usando los servicios públicos de cada módulo.
 *   <li>Historial de 90 días de ventas (inserción por lotes).
 *   <li>Parámetros de inventario a partir del historial.
 *   <li>Inventario inicial.
 *   <li>Reloj en {@code SIM_START_DATE 00:00}, en pausa.
 * </ol>
 *
 * Con la misma semilla, la carga da siempre el mismo resultado.
 */
@Component
public class DemoDataLoader {

  private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

  private final DemoFiles files;
  private final TenantDirectory tenants;
  private final HolidayCalendar holidays;
  private final UserDirectory users;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final PurchasingService purchasing;
  private final LogisticsService logistics;
  private final SalesService sales;
  private final FinanceService finance;
  private final AiFacade ai;
  private final HistoryGenerator history;
  private final DemoContext context;
  private final SimulationStateStore state;
  private final SimulationSettings settings;
  private final SimClock clock;

  DemoDataLoader(
      DemoFiles files,
      TenantDirectory tenants,
      HolidayCalendar holidays,
      UserDirectory users,
      CatalogService catalog,
      InventoryService inventory,
      PurchasingService purchasing,
      LogisticsService logistics,
      SalesService sales,
      FinanceService finance,
      AiFacade ai,
      HistoryGenerator history,
      DemoContext context,
      SimulationStateStore state,
      SimulationSettings settings,
      SimClock clock) {
    this.files = files;
    this.tenants = tenants;
    this.holidays = holidays;
    this.users = users;
    this.catalog = catalog;
    this.inventory = inventory;
    this.purchasing = purchasing;
    this.logistics = logistics;
    this.sales = sales;
    this.finance = finance;
    this.ai = ai;
    this.history = history;
    this.context = context;
    this.state = state;
    this.settings = settings;
    this.clock = clock;
  }

  @SuppressWarnings("unchecked")
  public long load() {
    long started = System.currentTimeMillis();
    LocalDate start = settings.startDate();
    // Mientras carga, el reloj marca la víspera para que las marcas de tiempo queden antes del día
    // 1.
    clock.set(SimTime.startOfDay(start).minusSeconds(60));
    Map<String, Object> company = files.yaml("company.yml");
    Map<String, Object> c = (Map<String, Object>) company.get("company");
    long companyId =
        tenants.createCompany(
            (String) c.get("name"),
            (String) c.get("nit"),
            (String) c.get("city"),
            (String) c.get("timezone"),
            (String) c.get("currency"));
    return TenantContext.call(
        companyId,
        () -> {
          for (Map<String, String> h : files.csv("holidays.csv")) {
            holidays.put(LocalDate.parse(h.get("date")), h.get("name"));
          }
          Map<String, Long> warehouseIds = new LinkedHashMap<>();
          for (Map<String, Object> w : (List<Map<String, Object>>) company.get("warehouses")) {
            WarehouseDto dto =
                inventory.createWarehouse(
                    (String) w.get("code"),
                    (String) w.get("name"),
                    (String) w.get("city"),
                    (String) w.get("address"));
            warehouseIds.put(dto.code(), dto.id());
          }
          String password = (String) company.get("password");
          for (Map<String, Object> u : (List<Map<String, Object>>) company.get("users")) {
            Object wh = u.get("warehouse");
            users.create(
                companyId,
                (String) u.get("email"),
                password,
                (String) u.get("name"),
                (String) u.get("role"),
                wh == null ? null : warehouseIds.get(wh.toString()));
          }
          loadCatalog();
          loadSuppliers(warehouseIds);
          loadCarriers();
          loadCustomers(warehouseIds);
          loadBudgets(company);
          ai.initializeCompany();
          loadInitialStock(warehouseIds);
          context.clearCache();
          int orders =
              history.generate(
                  start.minusDays(settings.historyDays()), start, settings.randomSeed());
          ai.initializeReorderParams(start);
          state.create(companyId, SimTime.startOfDay(start), settings.secondsPerDay());
          clock.set(SimTime.startOfDay(start));
          log.info(
              "Empresa demo cargada en {} ms: {} pedidos de historial",
              System.currentTimeMillis() - started,
              orders);
          return companyId;
        });
  }

  private void loadCatalog() {
    Map<String, Long> categories = new HashMap<>();
    for (Map<String, String> r : files.csv("categories.csv")) {
      CategoryDto cat = catalog.createCategory(r.get("code"), r.get("name"));
      categories.put(cat.code(), cat.id());
    }
    for (Map<String, String> r : files.csv("products.csv")) {
      String sku = r.get("sku");
      boolean perishable = Boolean.parseBoolean(r.get("perishable"));
      String shelf = r.get("shelf_life_days");
      catalog.create(
          new ProductCommand(
              sku,
              r.get("name"),
              categories.get(sku.substring(0, 3)),
              "und",
              Integer.parseInt(r.get("units_per_case")),
              new BigDecimal(r.get("weight_kg")),
              new BigDecimal(r.get("cost")),
              new BigDecimal(r.get("price")),
              perishable,
              shelf == null || shelf.isBlank() ? null : Integer.valueOf(shelf),
              true));
    }
  }

  @SuppressWarnings("unchecked")
  private void loadSuppliers(Map<String, Long> warehouseIds) {
    Map<String, Object> rules = files.yaml("supplier_offers.yml");
    int minCases = ((Number) rules.get("min_order_cases")).intValue();
    Map<String, Map<String, Object>> assortment =
        (Map<String, Map<String, Object>>) rules.get("assortment");
    Map<String, Object> preferred = (Map<String, Object>) rules.get("preferred");
    Map<String, String> prefByCategory = (Map<String, String>) preferred.get("categories");
    Map<String, String> prefBySku = (Map<String, String>) preferred.get("skus");
    Map<String, Object> bogota = (Map<String, Object>) rules.get("bogota_lead_time");
    int sameCity = ((Number) bogota.get("same_city_days")).intValue();
    int extra = ((Number) bogota.get("extra_days")).intValue();
    List<ProductDto> products = catalog.activeProducts();

    for (Map<String, String> r : files.csv("suppliers.csv")) {
      String code = r.get("code");
      SupplierDto s =
          purchasing.createSupplier(
              new SupplierCommand(
                  code,
                  r.get("name"),
                  r.get("nit"),
                  r.get("city"),
                  r.get("email"),
                  new BigDecimal(r.get("min_order_amount")),
                  Integer.parseInt(r.get("payment_terms_days")),
                  Double.parseDouble(r.get("on_time_rate")),
                  Double.parseDouble(r.get("fill_rate"))));
      int leadCal = Integer.parseInt(r.get("lead_time_cal"));
      purchasing.setLeadTime(s.id(), warehouseIds.get("CAL-01"), leadCal);
      purchasing.setLeadTime(
          s.id(),
          warehouseIds.get("BOG-01"),
          "Bogotá".equals(r.get("city")) ? sameCity : leadCal + extra);

      BigDecimal factor = new BigDecimal(r.get("price_factor"));
      Map<String, Object> range = assortment.getOrDefault(code, Map.of());
      Set<String> cats = new HashSet<>((List<String>) range.getOrDefault("categories", List.of()));
      Set<String> skus = new HashSet<>((List<String>) range.getOrDefault("skus", List.of()));
      Set<String> except = new HashSet<>((List<String>) range.getOrDefault("except", List.of()));
      for (ProductDto p : products) {
        boolean sells =
            (cats.contains(p.categoryCode()) || skus.contains(p.sku()))
                && !except.contains(p.sku());
        if (!sells) {
          continue;
        }
        String pref = prefBySku.getOrDefault(p.sku(), prefByCategory.get(p.categoryCode()));
        purchasing.upsertOffer(
            s.id(),
            p.id(),
            Money.roundToTens(p.cost().multiply(factor)),
            minCases,
            code.equals(pref));
      }
    }
  }

  private void loadCarriers() {
    for (Map<String, String> r : files.csv("carriers.csv")) {
      String coverage = r.get("coverage");
      logistics.createCarrier(
          new CarrierCommand(
              r.get("code"),
              r.get("name"),
              coverage == null || coverage.isBlank()
                  ? List.of()
                  : Arrays.asList(coverage.split("\\|")),
              Boolean.parseBoolean(r.get("national")),
              new BigDecimal(r.get("base_rate")),
              new BigDecimal(r.get("rate_per_kg")),
              Double.parseDouble(r.get("on_time_rate"))));
    }
  }

  private void loadCustomers(Map<String, Long> warehouseIds) {
    List<CustomerFactory.Spec> specs = new ArrayList<>();
    for (Map<String, String> r : files.csv("customer_segments.csv")) {
      sales.createSegment(r.get("code"), r.get("name"), new BigDecimal(r.get("discount_pct")));
      specs.add(new CustomerFactory.Spec(r.get("code"), Double.parseDouble(r.get("share"))));
    }
    var profile = files.profile();
    for (CustomerCommand c :
        CustomerFactory.build(
            profile.customers(), specs, warehouseIds, profile.cities(), settings.randomSeed())) {
      sales.createCustomer(c);
    }
  }

  @SuppressWarnings("unchecked")
  private void loadBudgets(Map<String, Object> company) {
    Map<String, Object> budgets = (Map<String, Object>) company.get("budgets");
    for (String period : (List<String>) company.get("budget_periods")) {
      budgets.forEach(
          (code, amount) ->
              catalog
                  .categoryByCode(code)
                  .ifPresent(
                      cat ->
                          finance.createBudget(
                              cat.id(), period, new BigDecimal(amount.toString()))));
    }
  }

  @SuppressWarnings("unchecked")
  private void loadInitialStock(Map<String, Long> warehouseIds) {
    Map<String, Object> rules = files.yaml("initial_stock.yml");
    int defaultCover = ((Number) rules.get("default_cover_days")).intValue();
    Map<String, Integer> exceptions = new HashMap<>();
    for (Map<String, Object> e : (List<Map<String, Object>>) rules.get("exceptions")) {
      exceptions.put(
          e.get("sku") + "@" + e.get("warehouse"), ((Number) e.get("cover_days")).intValue());
    }
    Map<String, Double> base = files.baseDemand();
    Map<String, Double> factors = files.warehouseFactors();
    for (ProductDto p : catalog.activeProducts()) {
      for (Map.Entry<String, Long> w : warehouseIds.entrySet()) {
        int cover = exceptions.getOrDefault(p.sku() + "@" + w.getKey(), defaultCover);
        double daily = base.getOrDefault(p.sku(), 0.0) * factors.getOrDefault(w.getKey(), 1.0);
        int qty = p.roundUpToCases((int) Math.ceil(daily * cover));
        inventory.setInitialStock(p.id(), w.getValue(), qty, p.cost());
      }
    }
  }
}
