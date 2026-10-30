package com.xperp.ai.tools;

import com.xperp.ai.decisions.AiDecision;
import com.xperp.ai.decisions.AiDecisionRepository;
import com.xperp.ai.forecasting.ForecastService;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.CategoryDto;
import com.xperp.catalog.api.ProductDto;
import com.xperp.dashboard.api.DashboardQueries;
import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.logistics.api.CarrierQuoteDto;
import com.xperp.logistics.api.LogisticsService;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.OpenLine;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.sales.api.CustomerSkuStats;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools de lectura del agente y del asistente (AGENTS.md, 8.6). Solo devuelven datos; nunca
 * escriben ni generan SQL. Registran cada llamada para guardarla en la conversación.
 */
@Component
public class ReadTools {

  private static final ThreadLocal<List<Map<String, Object>>> CALLS =
      ThreadLocal.withInitial(ArrayList::new);

  private final InventoryService inventory;
  private final CatalogService catalog;
  private final PurchasingService purchasing;
  private final FinanceService finance;
  private final SalesService sales;
  private final LogisticsService logistics;
  private final ForecastService forecasts;
  private final DashboardQueries dashboard;
  private final AiDecisionRepository decisions;
  private final Clock clock;

  ReadTools(
      InventoryService inventory,
      CatalogService catalog,
      PurchasingService purchasing,
      FinanceService finance,
      SalesService sales,
      LogisticsService logistics,
      ForecastService forecasts,
      DashboardQueries dashboard,
      AiDecisionRepository decisions,
      Clock clock) {
    this.inventory = inventory;
    this.catalog = catalog;
    this.purchasing = purchasing;
    this.finance = finance;
    this.sales = sales;
    this.logistics = logistics;
    this.forecasts = forecasts;
    this.dashboard = dashboard;
    this.decisions = decisions;
    this.clock = clock;
  }

  /** Devuelve y limpia las llamadas registradas en este hilo. */
  public List<Map<String, Object>> drainCalls() {
    List<Map<String, Object>> calls = new ArrayList<>(CALLS.get());
    CALLS.remove();
    return calls;
  }

  private void record(String tool, Map<String, Object> args) {
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("tool", tool);
    call.put("args", args);
    CALLS.get().add(call);
  }

  @Tool(
      name = "get_stock",
      description =
          "Existencias de un SKU en una bodega (o en todas): disponible, reservado, en tránsito, punto de reorden y días de cobertura.")
  public List<Map<String, Object>> getStock(
      @ToolParam(description = "SKU, por ejemplo ABA-001") String sku,
      @ToolParam(
              description = "Código de bodega (CAL-01 o BOG-01); vacío para todas",
              required = false)
          String warehouse) {
    record("get_stock", Map.of("sku", sku, "warehouse", warehouse == null ? "" : warehouse));
    ProductDto p = product(sku).orElse(null);
    if (p == null) {
      return List.of(Map.of("error", "SKU no encontrado: " + sku));
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (WarehouseDto w : warehouses(warehouse)) {
      result.add(stockRow(inventory.row(p.id(), w.id())));
    }
    return result;
  }

  @Tool(
      name = "list_low_stock",
      description =
          "SKU que se agotan pronto: días de cobertura menores al umbral, ordenados del más urgente al menos.")
  public List<Map<String, Object>> listLowStock(
      @ToolParam(description = "Umbral en días de cobertura, por ejemplo 7") Integer days) {
    int limit = days == null ? 7 : days;
    record("list_low_stock", Map.of("days", limit));
    return inventory.rows(null).stream()
        .filter(r -> r.stock().daysOfCover() != null && r.stock().daysOfCover() < limit)
        .sorted(Comparator.comparingDouble(r -> r.stock().daysOfCover()))
        .limit(25)
        .map(this::stockRow)
        .toList();
  }

  @Tool(
      name = "get_forecast",
      description = "Pronóstico de demanda de un SKU en una bodega para los próximos días.")
  public Map<String, Object> getForecast(
      @ToolParam(description = "SKU") String sku,
      @ToolParam(description = "Código de bodega") String warehouse,
      @ToolParam(description = "Días a pronosticar (1 a 30)") Integer days) {
    record(
        "get_forecast",
        Map.of("sku", sku, "warehouse", warehouse, "days", days == null ? 7 : days));
    ProductDto p = product(sku).orElse(null);
    Optional<WarehouseDto> w = inventory.warehouseByCode(warehouse);
    if (p == null || w.isEmpty()) {
      return Map.of("error", "SKU o bodega no encontrados");
    }
    int horizon = Math.max(1, Math.min(30, days == null ? 7 : days));
    Double total = forecasts.sum(p.id(), w.get().id(), SimTime.today(clock), horizon);
    return Map.of(
        "sku",
        p.sku(),
        "warehouse",
        warehouse,
        "days",
        horizon,
        "forecast_units",
        total == null ? "sin pronóstico" : Math.round(total));
  }

  @Tool(
      name = "list_supplier_offers",
      description =
          "Ofertas de los proveedores para un SKU: costo, tiempo de entrega a la bodega, cumplimiento y si es preferido.")
  public List<Map<String, Object>> listSupplierOffers(
      @ToolParam(description = "SKU") String sku,
      @ToolParam(description = "Código de bodega") String warehouse) {
    record("list_supplier_offers", Map.of("sku", sku, "warehouse", warehouse));
    ProductDto p = product(sku).orElse(null);
    Optional<WarehouseDto> w = inventory.warehouseByCode(warehouse);
    if (p == null || w.isEmpty()) {
      return List.of(Map.of("error", "SKU o bodega no encontrados"));
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (OfferDto o : purchasing.offersForProduct(p.id())) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("supplier", o.supplier().code() + " " + o.supplier().name());
      m.put("unit_cost", Money.format(o.unitCost()));
      m.put("lead_time_days", o.supplier().leadTimeTo(w.get().id()));
      m.put("on_time_rate", o.supplier().onTimeRate());
      m.put("preferred", o.preferred());
      result.add(m);
    }
    return result;
  }

  @Tool(
      name = "get_open_purchase_orders",
      description = "Órdenes de compra abiertas; si se da un SKU, solo las que lo incluyen.")
  public List<Map<String, Object>> getOpenPurchaseOrders(
      @ToolParam(description = "SKU; vacío para todas", required = false) String sku) {
    record("get_open_purchase_orders", Map.of("sku", sku == null ? "" : sku));
    Long productId =
        sku == null || sku.isBlank() ? null : product(sku).map(ProductDto::id).orElse(-1L);
    List<Map<String, Object>> result = new ArrayList<>();
    for (OpenLine l : purchasing.openLines()) {
      if (productId != null && l.productId() != productId) {
        continue;
      }
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("po", l.number());
      m.put("supplier", purchasing.supplier(l.supplierId()).name());
      m.put("sku", catalog.product(l.productId()).sku());
      m.put("pending_units", l.pendingQuantity());
      m.put("status", l.status());
      m.put(
          "expected_at",
          l.expectedAt() == null ? "sin confirmar" : SimTime.display(l.expectedAt()));
      result.add(m);
      if (result.size() >= 30) {
        break;
      }
    }
    return result;
  }

  @Tool(
      name = "get_budget_status",
      description =
          "Presupuesto de compras del mes para una categoría (ABA, BEB, ASE, CPE, SNK) o para todas.")
  public List<Map<String, Object>> getBudgetStatus(
      @ToolParam(description = "Código de categoría; vacío para todas", required = false)
          String category) {
    record("get_budget_status", Map.of("category", category == null ? "" : category));
    String period = SimTime.period(clock.instant());
    List<Map<String, Object>> result = new ArrayList<>();
    for (BudgetDto b : finance.budgets(period)) {
      CategoryDto c = catalog.category(b.categoryId());
      if (category != null && !category.isBlank() && !c.code().equalsIgnoreCase(category)) {
        continue;
      }
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("category", c.code() + " " + c.name());
      m.put("period", b.period());
      m.put("budget", Money.display(b.amount()));
      m.put("committed", Money.display(b.committed()));
      m.put("spent", Money.display(b.spent()));
      m.put("available", Money.display(b.available()));
      m.put("usage_pct", b.usagePct());
      result.add(m);
    }
    return result;
  }

  @Tool(
      name = "get_customer_history",
      description =
          "Historial de compras de un cliente para un SKU: cuántas veces lo pidió, cantidad promedio y máxima.")
  public Map<String, Object> getCustomerHistory(
      @ToolParam(description = "Id del cliente") Long customerId,
      @ToolParam(description = "SKU") String sku) {
    record("get_customer_history", Map.of("customer_id", customerId, "sku", sku));
    ProductDto p = product(sku).orElse(null);
    if (p == null) {
      return Map.of("error", "SKU no encontrado");
    }
    CustomerSkuStats stats = sales.customerHistory(customerId, List.of(p.id()), null).get(p.id());
    if (stats == null) {
      return Map.of("customer_id", customerId, "sku", sku, "lines", 0);
    }
    return Map.of(
        "customer", sales.customer(customerId).name(),
        "sku", sku,
        "lines", stats.lines(),
        "avg_quantity", Math.round(stats.avgQuantity() * 10) / 10.0,
        "max_quantity", stats.maxQuantity());
  }

  @Tool(
      name = "quote_carriers",
      description =
          "Cotiza el envío de un pedido de venta con las transportadoras que cubren la ciudad del cliente.")
  public List<Map<String, Object>> quoteCarriers(
      @ToolParam(description = "Id del pedido de venta") Long salesOrderId) {
    record("quote_carriers", Map.of("sales_order_id", salesOrderId));
    List<Map<String, Object>> result = new ArrayList<>();
    for (CarrierQuoteDto q : logistics.quote(salesOrderId)) {
      result.add(
          Map.of(
              "carrier", q.carrier().code() + " " + q.carrier().name(),
              "service", q.service(),
              "cost", Money.display(q.cost()),
              "eta_days", q.etaDays(),
              "on_time_rate", q.carrier().onTimeRate()));
    }
    return result;
  }

  @Tool(
      name = "query_kpis",
      description =
          "KPIs del negocio en un rango de fechas: ventas, margen, nivel de servicio, ventas perdidas, quiebres, compras y decisiones de la IA.")
  public Map<String, Object> queryKpis(
      @ToolParam(description = "Desde (aaaa-mm-dd); vacío para el inicio del mes", required = false)
          String from,
      @ToolParam(description = "Hasta (aaaa-mm-dd); vacío para hoy", required = false) String to,
      @ToolParam(description = "Código de bodega; vacío para todas", required = false)
          String warehouse) {
    record("query_kpis", Map.of("from", from == null ? "" : from, "to", to == null ? "" : to));
    LocalDate today = SimTime.today(clock);
    LocalDate f = from == null || from.isBlank() ? today.withDayOfMonth(1) : LocalDate.parse(from);
    LocalDate t = to == null || to.isBlank() ? today : LocalDate.parse(to);
    Long wh =
        warehouse == null || warehouse.isBlank()
            ? null
            : inventory.warehouseByCode(warehouse).map(WarehouseDto::id).orElse(null);
    DashboardQueries.KpiSnapshot k = dashboard.kpis(f, t, wh);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("desde", f.toString());
    m.put("hasta", t.toString());
    m.put("ventas_hoy", Money.display(k.salesToday()));
    m.put("ventas_periodo", Money.display(k.salesPeriod()));
    m.put("margen_bruto", Money.display(k.grossMargin()));
    m.put("margen_pct", k.grossMarginPct());
    m.put("nivel_servicio_pct", Math.round(k.serviceLevel() * 1000) / 10.0);
    m.put("unidades_perdidas", k.lostSalesUnits());
    m.put("sku_en_quiebre", k.stockoutSkus());
    m.put("oc_abiertas", k.openPurchaseOrders());
    m.put("oc_atrasadas", k.latePurchaseOrders());
    m.put("compras_periodo", Money.display(k.purchasesPeriod()));
    m.put("decisiones_ia", k.decisionsByStatus());
    return m;
  }

  @Tool(
      name = "find_decisions",
      description =
          "Busca decisiones recientes de la IA por texto (proveedor, SKU, número de OC) y devuelve su razonamiento.")
  public List<Map<String, Object>> findDecisions(
      @ToolParam(description = "Texto a buscar") String text) {
    record("find_decisions", Map.of("text", text));
    List<Map<String, Object>> result = new ArrayList<>();
    for (AiDecision d : searchDecisions(text)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", d.getId());
      m.put("tipo", d.getDecisionType());
      m.put("titulo", d.getTitle());
      m.put("razonamiento", d.getReasoning());
      m.put("estado", d.getStatus());
      m.put("cuando", SimTime.display(d.getSimTime()));
      result.add(m);
    }
    return result;
  }

  /** Busca por título; también acepta el nombre o código de un proveedor. */
  public List<AiDecision> searchDecisions(String text) {
    String q = text == null ? "" : text.trim();
    List<AiDecision> found = decisions.findTop20ByTitleContainingIgnoreCaseOrderByIdDesc(q);
    if (found.isEmpty()) {
      for (var s : purchasing.suppliers()) {
        if (s.name().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) {
          found = decisions.findTop20ByTitleContainingIgnoreCaseOrderByIdDesc(s.code());
          break;
        }
      }
    }
    return found.stream().limit(10).toList();
  }

  // ---------------------------------------------------------------- utilidades

  private Optional<ProductDto> product(String sku) {
    return sku == null
        ? Optional.empty()
        : catalog.productBySku(sku.trim().toUpperCase(Locale.ROOT));
  }

  private List<WarehouseDto> warehouses(String code) {
    if (code == null || code.isBlank()) {
      return inventory.warehouses();
    }
    return inventory.warehouseByCode(code.trim()).map(List::of).orElse(List.of());
  }

  private Map<String, Object> stockRow(InventoryRow r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("sku", r.sku());
    m.put("producto", r.productName());
    m.put("bodega", r.warehouseCode());
    m.put("disponible", r.stock().available());
    m.put("reservado", r.stock().reserved());
    m.put("en_transito", r.inTransit());
    m.put("punto_reorden", r.stock().reorderPoint());
    m.put("demanda_diaria", r.stock().avgDailyDemand());
    Double cover = r.stock().daysOfCover();
    m.put("dias_cobertura", cover == null ? "sin demanda" : Math.round(cover * 10) / 10.0);
    m.put("estado", r.status());
    return m;
  }
}
