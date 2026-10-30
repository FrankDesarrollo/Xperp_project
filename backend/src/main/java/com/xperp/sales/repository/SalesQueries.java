package com.xperp.sales.repository;

import com.xperp.sales.api.CustomerSkuStats;
import com.xperp.sales.api.DailyDemand;
import com.xperp.sales.api.SegmentDto;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas de agregación de ventas y la tabla de segmentos (llave natural compuesta). */
@Repository
public class SalesQueries {

  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;

  SalesQueries(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
    this.jdbc = jdbc;
    this.named = named;
  }

  public void insertSegment(String code, String name, BigDecimal discountPct) {
    jdbc.update(
        """
        insert into customer_segments (company_id, code, name, discount_pct)
        values (?, ?, ?, ?)
        on conflict (company_id, code) do update set name = excluded.name,
          discount_pct = excluded.discount_pct
        """,
        TenantContext.require(),
        code,
        name,
        discountPct);
  }

  public List<SegmentDto> segments() {
    return jdbc.query(
        "select code, name, discount_pct from customer_segments where company_id = ? order by code",
        (rs, i) ->
            new SegmentDto(
                rs.getString("code"), rs.getString("name"), rs.getBigDecimal("discount_pct")),
        TenantContext.require());
  }

  /**
   * Demanda pedida por día. Excluye los pedidos marcados como anomalía que siguen retenidos o que
   * se cancelaron: no se tratan como demanda real hasta que alguien los libere.
   */
  public List<DailyDemand> dailyDemand(LocalDate from, LocalDate to) {
    return jdbc.query(
        """
        select i.product_id, o.warehouse_id,
               (o.ordered_at at time zone 'America/Bogota')::date as day,
               sum(i.quantity) as qty
        from sales_order_items i
        join sales_orders o on o.id = i.sales_order_id
        where o.company_id = ? and o.ordered_at >= ? and o.ordered_at < ?
          and not (o.flagged and o.status in ('on_hold', 'cancelled'))
        group by i.product_id, o.warehouse_id, day
        """,
        (rs, i) ->
            new DailyDemand(
                rs.getLong("product_id"),
                rs.getLong("warehouse_id"),
                rs.getDate("day").toLocalDate(),
                rs.getInt("qty")),
        TenantContext.require(),
        Timestamp.from(SimTime.startOfDay(from)),
        Timestamp.from(SimTime.startOfDay(to.plusDays(1))));
  }

  public Map<Long, CustomerSkuStats> customerHistory(
      long customerId, Collection<Long> productIds, Long excludeOrderId) {
    Map<Long, CustomerSkuStats> result = new HashMap<>();
    if (productIds.isEmpty()) {
      return result;
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("company", TenantContext.require())
            .addValue("customer", customerId)
            .addValue("products", productIds)
            .addValue("exclude", excludeOrderId == null ? -1L : excludeOrderId);
    named.query(
        """
        select i.product_id, count(*) as lines, avg(i.quantity) as avg_qty, max(i.quantity) as max_qty
        from sales_order_items i
        join sales_orders o on o.id = i.sales_order_id
        where o.company_id = :company and o.customer_id = :customer
          and i.product_id in (:products) and o.id <> :exclude and not o.flagged
        group by i.product_id
        """,
        params,
        rs -> {
          long productId = rs.getLong("product_id");
          result.put(
              productId,
              new CustomerSkuStats(
                  productId, rs.getInt("lines"), rs.getDouble("avg_qty"), rs.getInt("max_qty")));
        });
    return result;
  }
}
