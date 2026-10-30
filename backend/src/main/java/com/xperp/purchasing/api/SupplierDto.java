package com.xperp.purchasing.api;

import java.math.BigDecimal;
import java.util.Map;

public record SupplierDto(
    long id,
    String code,
    String name,
    String nit,
    String city,
    String email,
    BigDecimal minOrderAmount,
    int paymentTermsDays,
    double onTimeRate,
    double fillRate,
    boolean active,
    Map<Long, Integer> leadTimes) {

  /** Tiempo de entrega a una bodega; null si no la atiende. */
  public Integer leadTimeTo(long warehouseId) {
    return leadTimes.get(warehouseId);
  }
}
