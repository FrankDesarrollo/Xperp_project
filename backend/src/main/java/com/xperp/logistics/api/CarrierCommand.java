package com.xperp.logistics.api;

import java.math.BigDecimal;
import java.util.List;

public record CarrierCommand(
    String code,
    String name,
    List<String> coverageCities,
    boolean national,
    BigDecimal baseRate,
    BigDecimal ratePerKg,
    double onTimeRate) {}
