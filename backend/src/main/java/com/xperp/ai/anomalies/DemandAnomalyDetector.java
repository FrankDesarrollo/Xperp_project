package com.xperp.ai.anomalies;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.sales.api.CustomerSkuStats;
import com.xperp.sales.api.SalesLine;
import com.xperp.sales.api.SalesOrderScreener;
import com.xperp.sales.api.SalesService;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Anomalía de venta (AGENTS.md, 8.4 demand_anomaly): una línea 5 veces mayor que lo habitual en ese
 * cliente y SKU retiene el pedido antes de reservar stock.
 */
@Component
class DemandAnomalyDetector implements SalesOrderScreener {

  static final double FACTOR = 5.0;

  /** Historia mínima del cliente con el SKU para juzgar. */
  static final int MIN_LINES = 5;

  /** Cantidad mínima para que valga la pena retener. */
  static final int MIN_UNITS = 24;

  private final SalesService sales;
  private final CatalogService catalog;

  DemandAnomalyDetector(@Lazy SalesService sales, CatalogService catalog) {
    this.sales = sales;
    this.catalog = catalog;
  }

  @Override
  public Optional<String> screen(ScreeningRequest request) {
    Map<Long, CustomerSkuStats> history =
        sales.customerHistory(
            request.customerId(),
            request.lines().stream().map(SalesLine::productId).toList(),
            null);
    for (SalesLine line : request.lines()) {
      CustomerSkuStats stats = history.get(line.productId());
      if (isAnomalous(line.quantity(), stats)) {
        ProductDto p = catalog.product(line.productId());
        return Optional.of(
            "%s: %d und, %.1f veces el promedio del cliente (%.0f und en %d pedidos)"
                .formatted(
                    p.sku(),
                    line.quantity(),
                    line.quantity() / stats.avgQuantity(),
                    stats.avgQuantity(),
                    stats.lines()));
      }
    }
    return Optional.empty();
  }

  static boolean isAnomalous(int quantity, CustomerSkuStats stats) {
    return stats != null
        && stats.lines() >= MIN_LINES
        && quantity >= MIN_UNITS
        && stats.avgQuantity() > 0
        && quantity > FACTOR * stats.avgQuantity();
  }
}
