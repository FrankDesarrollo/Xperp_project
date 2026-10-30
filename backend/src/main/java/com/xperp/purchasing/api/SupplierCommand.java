package com.xperp.purchasing.api;

import java.math.BigDecimal;

public record SupplierCommand(
    String code,
    String name,
    String nit,
    String city,
    String email,
    BigDecimal minOrderAmount,
    int paymentTermsDays,
    double onTimeRate,
    double fillRate) {}
