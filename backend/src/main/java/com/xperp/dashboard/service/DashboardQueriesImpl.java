package com.xperp.dashboard.service;

import com.xperp.dashboard.api.DashboardQueries;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPIs del tablero (AGENTS.md, 9.5). Es un modelo de lectura: consulta las tablas de los módulos
 * con SQL de solo lectura en vez de llamar sus servicios, porque cruza muchos datos a la vez. Ver
 * ADR 0003.
 */
@Service
@Transactional(readOnly = true)
class DashboardQueriesImpl implements DashboardQueries {

  private static final String VALID_ORDER =
      "o.status not in ('cancelled','on_hold') and not o.flagged";

  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  DashboardQueriesImpl(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public KpiSnapshot kpis(LocalDate from, LocalDate to, Long warehouseId) {
    LocalDate today = SimTime.today(clock);
    MapSqlParameterSource p = params(from, to, warehouseId);
    p.addValue("todayFrom", Timestamp.from(SimTime.startOfDay(today)));
    p.addValue("todayTo", Timestamp.from(SimTime.startOfDay(today.plusDays(1))));
    p.addValue("now", Timestamp.from(clock.instant()));
    String wh = warehouseId == null ? "" : " and o.warehouse_id = :wh";

    BigDecimal salesToday =
        money(
            "select coalesce(sum(o.total),0) from sales_orders o where o.company_id = :c and "
                + VALID_ORDER
                + " and o.ordered_at >= :todayFrom and o.ordered_at < :todayTo"
                + wh,
            p);
    Integer ordersToday =
        integer(
            "select count(*) from sales_orders o where o.company_id = :c and o.ordered_at >= :todayFrom and o.ordered_at < :todayTo"
                + wh,
            p);
    BigDecimal sales =
        money(
            "select coalesce(sum(o.total),0) from sales_orders o where o.company_id = :c and "
                + VALID_ORDER
                + " and o.ordered_at >= :from and o.ordered_at < :to"
                + wh,
            p);
    BigDecimal cogs =
        money(
            """
        select coalesce(sum(i.quantity_fulfilled * pr.cost),0)
        from sales_order_items i join sales_orders o on o.id = i.sales_order_id
        join products pr on pr.id = i.product_id
        where o.company_id = :c and %s and o.ordered_at >= :from and o.ordered_at < :to %s
        """
                .formatted(VALID_ORDER, wh),
            p);
    BigDecimal margin = sales.subtract(cogs);
    double marginPct = sales.signum() == 0 ? 0 : pct(margin, sales);

    Map<String, Object> service =
        jdbc.queryForMap(
            """
        select coalesce(sum(i.quantity),0) as asked, coalesce(sum(i.quantity_fulfilled),0) as served
        from sales_order_items i join sales_orders o on o.id = i.sales_order_id
        where o.company_id = :c and not o.flagged and o.status <> 'on_hold'
          and o.ordered_at >= :from and o.ordered_at < :to %s
        """
                .formatted(wh),
            p);
    double asked = ((Number) service.get("asked")).doubleValue();
    double served = ((Number) service.get("served")).doubleValue();
    double serviceLevel = asked == 0 ? 1 : served / asked;

    String whLost = warehouseId == null ? "" : " and l.warehouse_id = :wh";
    Map<String, Object> lost =
        jdbc.queryForMap(
            """
        select coalesce(sum(l.quantity),0) as units, coalesce(sum(l.quantity * pr.price),0) as amount
        from lost_sales l join products pr on pr.id = l.product_id
        where l.company_id = :c and l.occurred_at >= :from and l.occurred_at < :to %s
        """
                .formatted(whLost),
            p);

    String whInv = warehouseId == null ? "" : " and s.warehouse_id = :wh";
    Map<String, Object> stock =
        jdbc.queryForMap(
            """
        select count(*) filter (where s.on_hand - s.reserved <= 0) as stockout,
               count(*) filter (where s.on_hand - s.reserved > 0 and s.on_hand - s.reserved <= s.reorder_point) as low,
               coalesce(sum(s.on_hand * pr.cost),0) as value
        from inventory s join products pr on pr.id = s.product_id
        where s.company_id = :c %s
        """
                .formatted(whInv),
            p);
    BigDecimal inventoryValue = Money.round(new BigDecimal(stock.get("value").toString()));

    String whPo = warehouseId == null ? "" : " and po.warehouse_id = :wh";
    Map<String, Object> pos =
        jdbc.queryForMap(
            """
        select count(*) filter (where po.status in ('draft','pending_approval','sent','confirmed','delayed','shipped')) as open,
               count(*) filter (where po.status in ('sent','confirmed','delayed','shipped')
                                and coalesce(po.first_expected_at, po.expected_at) < :now) as late
        from purchase_orders po where po.company_id = :c %s
        """
                .formatted(whPo),
            p);
    BigDecimal purchases =
        money(
            "select coalesce(sum(po.total),0) from purchase_orders po where po.company_id = :c and po.status <> 'cancelled' and po.sent_at >= :from and po.sent_at < :to"
                + whPo,
            p);

    p.addValue("period", "%04d-%02d".formatted(to.getYear(), to.getMonthValue()));
    Map<String, Object> budget =
        jdbc.queryForMap(
            "select coalesce(sum(amount),0) as amount, coalesce(sum(committed + spent),0) as used from budgets where company_id = :c and period = :period",
            p);
    BigDecimal budgetAmount = new BigDecimal(budget.get("amount").toString());
    double budgetPct =
        budgetAmount.signum() == 0
            ? 0
            : pct(new BigDecimal(budget.get("used").toString()), budgetAmount);

    double rotation =
        inventoryValue.signum() == 0
            ? 0
            : cogs.divide(inventoryValue, 4, RoundingMode.HALF_UP).doubleValue();

    Map<String, Integer> decisions = new LinkedHashMap<>();
    for (String s :
        List.of("executed", "pending_approval", "suggested", "blocked", "approved", "rejected")) {
      decisions.put(s, 0);
    }
    jdbc.query(
        "select status, count(*) as n from ai_decisions where company_id = :c and sim_time >= :from and sim_time < :to group by status",
        p,
        rs -> {
          decisions.merge(rs.getString("status"), rs.getInt("n"), Integer::sum);
        });

    Double avgApproval =
        jdbc.queryForObject(
            "select avg(extract(epoch from (decided_at - requested_at)) / 60) from approvals where company_id = :c and status in ('approved','rejected') and decided_at >= :from and decided_at < :to",
            p,
            Double.class);

    BigDecimal overcost =
        money(
            """
        select coalesce(sum(d.amount - (d.input_snapshot->>'total_pendiente')::numeric), 0)
        from ai_decisions d
        where d.company_id = :c and d.action_type = 'reassign_purchase_order'
          and d.status in ('executed','approved') and d.sim_time >= :from and d.sim_time < :to
          and d.input_snapshot->>'total_pendiente' is not null
        """,
            p);

    List<CategoryDays> days = new ArrayList<>();
    jdbc.query(
        """
        select c.code, c.name, sum(s.on_hand) as units, sum(s.avg_daily_demand) as demand
        from inventory s join products pr on pr.id = s.product_id join categories c on c.id = pr.category_id
        where s.company_id = :c %s
        group by c.code, c.name order by c.code
        """
            .formatted(whInv),
        p,
        rs -> {
          double demand = rs.getDouble("demand");
          days.add(
              new CategoryDays(
                  rs.getString("code"),
                  rs.getString("name"),
                  demand <= 0 ? 0 : Math.round(rs.getDouble("units") / demand * 10) / 10.0));
        });

    List<SupplierFulfillment> suppliers = new ArrayList<>();
    jdbc.query(
        """
        select s.code, s.name, s.on_time_rate,
               count(po.id) filter (where po.status = 'received') as received,
               count(po.id) filter (where po.status = 'received'
                 and po.received_at <= coalesce(po.first_expected_at, po.expected_at) + interval '12 hours') as on_time
        from suppliers s left join purchase_orders po on po.supplier_id = s.id and po.sent_at >= :from and po.sent_at < :to
        where s.company_id = :c
        group by s.code, s.name, s.on_time_rate order by s.code
        """,
        p,
        rs -> {
          int received = rs.getInt("received");
          double rate =
              received == 0
                  ? rs.getDouble("on_time_rate")
                  : (double) rs.getInt("on_time") / received;
          suppliers.add(
              new SupplierFulfillment(
                  rs.getString("code"),
                  rs.getString("name"),
                  received,
                  Math.round(rate * 1000) / 1000.0));
        });

    return new KpiSnapshot(
        from,
        to,
        salesToday,
        sales,
        ordersToday,
        Money.round(margin),
        marginPct,
        Math.round(serviceLevel * 10000) / 10000.0,
        ((Number) lost.get("units")).intValue(),
        Money.round(new BigDecimal(lost.get("amount").toString())),
        ((Number) stock.get("stockout")).intValue(),
        ((Number) stock.get("low")).intValue(),
        ((Number) pos.get("open")).intValue(),
        ((Number) pos.get("late")).intValue(),
        purchases,
        budgetPct,
        inventoryValue,
        rotation,
        decisions,
        avgApproval == null ? null : Math.round(avgApproval * 10) / 10.0,
        Money.round(overcost),
        days,
        suppliers);
  }

  @Override
  public List<Point> series(String metric, LocalDate from, LocalDate to, Long warehouseId) {
    MapSqlParameterSource p = params(from, to, warehouseId);
    String wh = warehouseId == null ? "" : " and o.warehouse_id = :wh";
    String sql =
        switch (metric) {
          case "sales" ->
              "select (o.ordered_at at time zone 'America/Bogota')::date as d, sum(o.total) as v from sales_orders o where o.company_id = :c and "
                  + VALID_ORDER
                  + " and o.ordered_at >= :from and o.ordered_at < :to"
                  + wh
                  + " group by d order by d";
          case "orders" ->
              "select (o.ordered_at at time zone 'America/Bogota')::date as d, count(*) as v from sales_orders o where o.company_id = :c and o.ordered_at >= :from and o.ordered_at < :to"
                  + wh
                  + " group by d order by d";
          case "service_level" ->
              """
              select (o.ordered_at at time zone 'America/Bogota')::date as d,
                     sum(i.quantity_fulfilled)::float / nullif(sum(i.quantity), 0) as v
              from sales_order_items i join sales_orders o on o.id = i.sales_order_id
              where o.company_id = :c and not o.flagged and o.status <> 'on_hold' and o.ordered_at >= :from and o.ordered_at < :to %s
              group by d order by d
              """
                  .formatted(wh);
          case "lost_sales" ->
              "select (l.occurred_at at time zone 'America/Bogota')::date as d, sum(l.quantity) as v from lost_sales l where l.company_id = :c and l.occurred_at >= :from and l.occurred_at < :to"
                  + (warehouseId == null ? "" : " and l.warehouse_id = :wh")
                  + " group by d order by d";
          case "purchases" ->
              "select (po.sent_at at time zone 'America/Bogota')::date as d, sum(po.total) as v from purchase_orders po where po.company_id = :c and po.status <> 'cancelled' and po.sent_at >= :from and po.sent_at < :to"
                  + (warehouseId == null ? "" : " and po.warehouse_id = :wh")
                  + " group by d order by d";
          default -> throw new IllegalArgumentException("Métrica desconocida: " + metric);
        };
    Map<LocalDate, Double> values = new LinkedHashMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      values.put(d, 0.0);
    }
    jdbc.query(
        sql,
        p,
        rs -> {
          values.put(rs.getDate("d").toLocalDate(), rs.getDouble("v"));
        });
    if ("service_level".equals(metric)) {
      values.replaceAll((d, v) -> v == 0 ? null : v);
    }
    List<Point> points = new ArrayList<>();
    values.forEach(
        (d, v) ->
            points.add(new Point(d, v == null ? Double.NaN : Math.round(v * 10000) / 10000.0)));
    return points.stream().filter(pt -> !Double.isNaN(pt.value())).toList();
  }

  private MapSqlParameterSource params(LocalDate from, LocalDate to, Long warehouseId) {
    Instant start = SimTime.startOfDay(from);
    Instant end = SimTime.startOfDay(to.plusDays(1));
    return new MapSqlParameterSource()
        .addValue("c", TenantContext.require())
        .addValue("from", Timestamp.from(start))
        .addValue("to", Timestamp.from(end))
        .addValue("wh", warehouseId);
  }

  private BigDecimal money(String sql, MapSqlParameterSource p) {
    BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
    return Money.round(v == null ? BigDecimal.ZERO : v);
  }

  private Integer integer(String sql, MapSqlParameterSource p) {
    Integer v = jdbc.queryForObject(sql, p, Integer.class);
    return v == null ? 0 : v;
  }

  private static double pct(BigDecimal part, BigDecimal whole) {
    return part.multiply(BigDecimal.valueOf(100))
        .divide(whole, 2, RoundingMode.HALF_UP)
        .doubleValue();
  }
}
