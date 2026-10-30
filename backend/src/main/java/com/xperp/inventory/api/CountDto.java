package com.xperp.inventory.api;

import java.time.Instant;

public record CountDto(
    long id,
    long productId,
    long warehouseId,
    int systemQty,
    int countedQty,
    String status,
    String countedBy,
    Instant countedAt) {

  public int difference() {
    return countedQty - systemQty;
  }

  /** Diferencia en porcentaje del sistema, con signo. */
  public double differencePct() {
    return systemQty == 0 ? (countedQty == 0 ? 0 : 100) : 100.0 * difference() / systemQty;
  }
}
