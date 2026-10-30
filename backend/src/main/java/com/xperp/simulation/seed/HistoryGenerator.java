package com.xperp.simulation.seed;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.sales.api.CustomerDto;
import com.xperp.sales.api.SalesLine;
import com.xperp.sales.api.SalesService;
import com.xperp.sales.api.SegmentDto;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.simulation.demand.OrderPlanner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Historial de 90 días de ventas con el mismo patrón de demanda (AGENTS.md, 2.14 paso 2). Para que
 * sea rápido usa inserción por lotes con JdbcTemplate: es la única excepción a la regla de pasar
 * por los servicios de los módulos.
 */
@Component
class HistoryGenerator {

  private static final Logger log = LoggerFactory.getLogger(HistoryGenerator.class);

  private final JdbcTemplate jdbc;
  private final DemoContext context;
  private final CatalogService catalog;
  private final SalesService sales;

  HistoryGenerator(
      JdbcTemplate jdbc, DemoContext context, CatalogService catalog, SalesService sales) {
    this.jdbc = jdbc;
    this.context = context;
    this.catalog = catalog;
    this.sales = sales;
  }

  /** Genera los pedidos entre {@code from} y el día anterior a {@code until}. */
  int generate(LocalDate from, LocalDate until, long seed) {
    long companyId = TenantContext.require();
    Map<Long, ProductDto> products = new HashMap<>();
    catalog.activeProducts().forEach(p -> products.put(p.id(), p));
    Map<String, BigDecimal> discounts = new HashMap<>();
    for (SegmentDto s : sales.segments()) {
      discounts.put(s.code(), s.discountPct());
    }
    Map<Long, CustomerDto> customers = new HashMap<>();
    sales.activeCustomers().forEach(c -> customers.put(c.id(), c));

    record Row(
        String number,
        OrderPlanner.PlannedOrder order,
        long warehouseId,
        BigDecimal subtotal,
        BigDecimal discount) {}
    List<Row> rows = new ArrayList<>();
    int seq = 0;
    for (LocalDate d = from; d.isBefore(until); d = d.plusDays(1)) {
      List<OrderPlanner.PlannedOrder> day = new ArrayList<>();
      Map<OrderPlanner.PlannedOrder, Long> warehouseOf = new HashMap<>();
      for (DemoContext.WarehouseDemand w : context.snapshot().warehouses()) {
        for (OrderPlanner.PlannedOrder o : context.plan(d, w, seed)) {
          day.add(o);
          warehouseOf.put(o, w.warehouse().id());
        }
      }
      day.sort((a, b) -> a.at().compareTo(b.at()));
      for (OrderPlanner.PlannedOrder o : day) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SalesLine l : o.lines()) {
          subtotal =
              subtotal.add(
                  products.get(l.productId()).price().multiply(BigDecimal.valueOf(l.quantity())));
        }
        BigDecimal pct =
            discounts.getOrDefault(customers.get(o.customerId()).segmentCode(), BigDecimal.ZERO);
        BigDecimal discount =
            subtotal.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        int year = d.getYear();
        rows.add(
            new Row(
                "PV-%d-%06d".formatted(year, ++seq),
                o,
                warehouseOf.get(o),
                subtotal.setScale(2, RoundingMode.HALF_UP),
                discount));
      }
    }

    List<Object[]> orderArgs = new ArrayList<>();
    for (Row r : rows) {
      Timestamp at = Timestamp.from(r.order().at());
      orderArgs.add(
          new Object[] {
            companyId,
            r.number(),
            r.order().customerId(),
            r.warehouseId(),
            r.subtotal(),
            r.discount(),
            r.subtotal().subtract(r.discount()),
            at,
            Timestamp.from(r.order().at().plus(Duration.ofDays(2))),
            at,
            at
          });
    }
    jdbc.batchUpdate(
        """
        insert into sales_orders (company_id, number, customer_id, warehouse_id, status, subtotal,
          discount, total, ordered_at, promised_at, created_at, updated_at)
        values (?, ?, ?, ?, 'delivered', ?, ?, ?, ?, ?, ?, ?)
        """,
        orderArgs);

    Map<String, Long> ids = new HashMap<>();
    jdbc.query(
        "select id, number from sales_orders where company_id = ?",
        rs -> {
          ids.put(rs.getString("number"), rs.getLong("id"));
        },
        companyId);
    List<Object[]> itemArgs = new ArrayList<>();
    for (Row r : rows) {
      long orderId = ids.get(r.number());
      Timestamp at = Timestamp.from(r.order().at());
      for (SalesLine l : r.order().lines()) {
        itemArgs.add(
            new Object[] {
              companyId,
              orderId,
              l.productId(),
              l.quantity(),
              l.quantity(),
              products.get(l.productId()).price(),
              at,
              at
            });
      }
    }
    for (int i = 0; i < itemArgs.size(); i += 2000) {
      jdbc.batchUpdate(
          """
          insert into sales_order_items (company_id, sales_order_id, product_id, quantity,
            quantity_fulfilled, unit_price, created_at, updated_at)
          values (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          itemArgs.subList(i, Math.min(i + 2000, itemArgs.size())));
    }
    if (!rows.isEmpty()) {
      jdbc.update(
          """
          insert into document_sequences (company_id, prefix, year, last_value) values (?, 'PV', ?, ?)
          on conflict (company_id, prefix, year) do update set last_value = excluded.last_value
          """,
          companyId,
          until.minusDays(1).getYear(),
          seq);
    }
    log.info("Historial generado: {} pedidos y {} líneas", rows.size(), itemArgs.size());
    return rows.size();
  }
}
