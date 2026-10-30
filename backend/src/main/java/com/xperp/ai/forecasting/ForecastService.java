package com.xperp.ai.forecasting;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockKey;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.sales.api.DailyDemand;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.tenancy.TenantContext;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pronóstico de demanda por SKU y bodega a 30 días (AGENTS.md, sección 8.2). Se guarda en {@code
 * demand_forecasts} cada día simulado a las 05:00.
 */
@Service
public class ForecastService {

  private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

  public static final int HORIZON_DAYS = 30;
  static final int HISTORY_DAYS = 120;
  static final int MIN_HISTORY_FOR_HW = 28;
  public static final String MODEL_HW = "holt_winters";
  public static final String MODEL_WMA = "weighted_moving_average";

  /** Serie histórica y pronóstico de una existencia. */
  public record Series(
      long productId,
      long warehouseId,
      String model,
      Map<LocalDate, Double> history,
      Map<LocalDate, Double> forecast) {}

  private final SalesService sales;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final CalendarFactors factors;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  ForecastService(
      SalesService sales,
      CatalogService catalog,
      InventoryService inventory,
      CalendarFactors factors,
      JdbcTemplate jdbc,
      Clock clock) {
    this.sales = sales;
    this.catalog = catalog;
    this.inventory = inventory;
    this.factors = factors;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Demanda diaria histórica por existencia, con ceros en los días sin venta. */
  public Map<StockKey, double[]> history(LocalDate from, LocalDate toInclusive) {
    int days = (int) ChronoUnit.DAYS.between(from, toInclusive) + 1;
    Map<StockKey, double[]> series = new HashMap<>();
    for (ProductDto p : catalog.activeProducts()) {
      for (WarehouseDto w : inventory.warehouses()) {
        series.put(new StockKey(p.id(), w.id()), new double[days]);
      }
    }
    for (DailyDemand d : sales.dailyDemand(from, toInclusive)) {
      double[] s = series.get(new StockKey(d.productId(), d.warehouseId()));
      if (s != null) {
        int idx = (int) ChronoUnit.DAYS.between(from, d.date());
        if (idx >= 0 && idx < days) {
          s[idx] += d.quantity();
        }
      }
    }
    return series;
  }

  /** Genera y guarda el pronóstico desde {@code today} (incluido) para todas las existencias. */
  @Transactional
  public int generate(LocalDate today) {
    LocalDate from = today.minusDays(HISTORY_DAYS);
    LocalDate to = today.minusDays(1);
    Map<StockKey, double[]> history = history(from, to);
    long companyId = TenantContext.require();
    Timestamp generatedAt = Timestamp.from(clock.instant());
    List<Object[]> rows = new ArrayList<>();
    for (Map.Entry<StockKey, double[]> e : history.entrySet()) {
      ProductDto product = catalog.product(e.getKey().productId());
      Map<LocalDate, Double> forecast =
          forecastSeries(e.getValue(), from, today, product.categoryCode());
      String model = trimmedLength(e.getValue()) >= MIN_HISTORY_FOR_HW ? MODEL_HW : MODEL_WMA;
      forecast.forEach(
          (date, qty) ->
              rows.add(
                  new Object[] {
                    companyId,
                    e.getKey().productId(),
                    e.getKey().warehouseId(),
                    Date.valueOf(date),
                    Math.round(qty * 100) / 100.0,
                    model,
                    generatedAt
                  }));
    }
    jdbc.update(
        "delete from demand_forecasts where company_id = ? and forecast_date < ?",
        companyId,
        Date.valueOf(today.minusDays(60)));
    jdbc.batchUpdate(
        """
        insert into demand_forecasts
          (company_id, product_id, warehouse_id, forecast_date, quantity, model, generated_at)
        values (?, ?, ?, ?, ?, ?, ?)
        on conflict (product_id, warehouse_id, forecast_date)
        do update set quantity = excluded.quantity, model = excluded.model,
          generated_at = excluded.generated_at, updated_at = now()
        """,
        rows);
    log.info("Pronóstico generado para {} existencias ({} filas)", history.size(), rows.size());
    return rows.size();
  }

  /**
   * Pronóstico de los próximos 30 días a partir de una serie. Quita los factores de calendario de
   * la historia, ajusta el modelo y vuelve a aplicar los factores de cada día futuro.
   */
  Map<LocalDate, Double> forecastSeries(
      double[] raw, LocalDate seriesStart, LocalDate firstForecastDay, String categoryCode) {
    // Se ignoran los días iniciales sin historia (productos nuevos).
    int start = 0;
    while (start < raw.length && raw[start] == 0) {
      start++;
    }
    int n = raw.length - start;
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      LocalDate date = seriesStart.plusDays(start + i);
      y[i] = raw[start + i] / factors.factor(date, categoryCode);
    }
    Map<LocalDate, Double> result = new LinkedHashMap<>();
    if (n >= MIN_HISTORY_FOR_HW) {
      // La estacionalidad se alinea con el día de la semana del primer dato.
      HoltWinters.Model model = HoltWinters.fit(y, 7);
      for (int h = 1; h <= HORIZON_DAYS; h++) {
        LocalDate date = firstForecastDay.plusDays(h - 1);
        result.put(date, model.forecast(h, n) * factors.factor(date, categoryCode));
      }
    } else {
      double base = HoltWinters.weightedMovingAverage(y, 14);
      for (int h = 1; h <= HORIZON_DAYS; h++) {
        LocalDate date = firstForecastDay.plusDays(h - 1);
        result.put(date, base * factors.factor(date, categoryCode));
      }
    }
    return result;
  }

  private static int trimmedLength(double[] raw) {
    int start = 0;
    while (start < raw.length && raw[start] == 0) {
      start++;
    }
    return raw.length - start;
  }

  /** Suma del pronóstico guardado entre {@code from} y {@code from + days − 1}; null si no hay. */
  @Transactional(readOnly = true)
  public Double sum(long productId, long warehouseId, LocalDate from, int days) {
    List<Double> values =
        jdbc.queryForList(
            """
            select quantity from demand_forecasts
            where product_id = ? and warehouse_id = ? and forecast_date >= ? and forecast_date < ?
            """,
            Double.class,
            productId,
            warehouseId,
            Date.valueOf(from),
            Date.valueOf(from.plusDays(days)));
    if (values.isEmpty()) {
      return null;
    }
    double sum = values.stream().mapToDouble(Double::doubleValue).sum();
    // Si el horizonte pasa de lo pronosticado, se extiende con el promedio.
    if (values.size() < days) {
      sum += (sum / values.size()) * (days - values.size());
    }
    return sum;
  }

  /** Todas las existencias: suma del pronóstico en el horizonte indicado. */
  @Transactional(readOnly = true)
  public Map<StockKey, Double> sums(LocalDate from, int days) {
    Map<StockKey, Double> result = new HashMap<>();
    jdbc.query(
        """
        select product_id, warehouse_id, sum(quantity) as qty, count(*) as n
        from demand_forecasts
        where company_id = ? and forecast_date >= ? and forecast_date < ?
        group by product_id, warehouse_id
        """,
        rs -> {
          double qty = rs.getDouble("qty");
          int n = rs.getInt("n");
          if (n < days && n > 0) {
            qty += (qty / n) * (days - n);
          }
          result.put(new StockKey(rs.getLong("product_id"), rs.getLong("warehouse_id")), qty);
        },
        TenantContext.require(),
        Date.valueOf(from),
        Date.valueOf(from.plusDays(days)));
    return result;
  }

  /** Historia y pronóstico guardado para la pantalla de detalle de inventario. */
  @Transactional(readOnly = true)
  public Series series(long productId, long warehouseId, LocalDate today, int historyDays) {
    LocalDate from = today.minusDays(historyDays);
    double[] hist = history(from, today.minusDays(1)).get(new StockKey(productId, warehouseId));
    Map<LocalDate, Double> history = new LinkedHashMap<>();
    if (hist != null) {
      for (int i = 0; i < hist.length; i++) {
        history.put(from.plusDays(i), hist[i]);
      }
    }
    Map<LocalDate, Double> forecast = new LinkedHashMap<>();
    String[] model = {MODEL_HW};
    jdbc.query(
        """
        select forecast_date, quantity, model from demand_forecasts
        where product_id = ? and warehouse_id = ? and forecast_date >= ?
        order by forecast_date
        """,
        rs -> {
          forecast.put(rs.getDate("forecast_date").toLocalDate(), rs.getDouble("quantity"));
          model[0] = rs.getString("model");
        },
        productId,
        warehouseId,
        Date.valueOf(today));
    return new Series(productId, warehouseId, model[0], history, forecast);
  }
}
