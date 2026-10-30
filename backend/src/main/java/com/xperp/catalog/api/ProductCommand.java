package com.xperp.catalog.api;

import java.math.BigDecimal;

public record ProductCommand(
    String sku,
    String name,
    long categoryId,
    String unit,
    int unitsPerCase,
    BigDecimal weightKg,
    BigDecimal cost,
    BigDecimal price,
    boolean perishable,
    Integer shelfLifeDays,
    boolean active) {}
