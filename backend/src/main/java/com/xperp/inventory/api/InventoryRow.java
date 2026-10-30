package com.xperp.inventory.api;

import java.math.BigDecimal;

/** Fila de la pantalla de inventario: existencia, producto, en tránsito y semáforo. */
public record InventoryRow(
    StockDto stock,
    String sku,
    String productName,
    String categoryCode,
    int unitsPerCase,
    String warehouseCode,
    int inTransit,
    String status,
    BigDecimal unitCost) {

  public static final String STOCKOUT = "stockout";
  public static final String LOW = "low";
  public static final String OK = "ok";
  public static final String OVER = "over";

  public BigDecimal stockValue() {
    return unitCost.multiply(BigDecimal.valueOf(stock.onHand()));
  }
}
