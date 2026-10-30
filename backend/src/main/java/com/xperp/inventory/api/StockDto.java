package com.xperp.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;

public record StockDto(
    long productId,
    long warehouseId,
    int onHand,
    int reserved,
    int safetyStock,
    int reorderPoint,
    int maxStock,
    int coverDays,
    BigDecimal avgDailyDemand,
    boolean promotionSuggested,
    boolean reorderBlocked,
    Instant paramsUpdatedAt) {

  public int available() {
    return onHand - reserved;
  }

  public StockKey key() {
    return new StockKey(productId, warehouseId);
  }

  /** Días que alcanza el disponible con la demanda promedio; null si no hay demanda. */
  public Double daysOfCover() {
    double demand = avgDailyDemand == null ? 0 : avgDailyDemand.doubleValue();
    return demand <= 0 ? null : available() / demand;
  }
}
