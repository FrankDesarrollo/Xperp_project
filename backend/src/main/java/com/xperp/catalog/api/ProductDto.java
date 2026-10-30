package com.xperp.catalog.api;

import java.math.BigDecimal;

public record ProductDto(
    long id,
    String sku,
    String name,
    long categoryId,
    String categoryCode,
    String categoryName,
    String unit,
    int unitsPerCase,
    BigDecimal weightKg,
    BigDecimal cost,
    BigDecimal price,
    boolean perishable,
    Integer shelfLifeDays,
    boolean active) {

  /** Margen unitario: precio − costo. */
  public BigDecimal unitMargin() {
    return price.subtract(cost);
  }

  /** Redondea hacia arriba a cajas completas. */
  public int roundUpToCases(int units) {
    if (units <= 0) {
      return 0;
    }
    return ((units + unitsPerCase - 1) / unitsPerCase) * unitsPerCase;
  }
}
