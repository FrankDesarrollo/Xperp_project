package com.xperp.sales.api;

import java.math.BigDecimal;

public record SalesOrderLineDto(
    long id, long productId, int quantity, int quantityFulfilled, BigDecimal unitPrice) {

  public BigDecimal lineTotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantityFulfilled));
  }
}
