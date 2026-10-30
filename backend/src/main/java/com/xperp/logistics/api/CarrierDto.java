package com.xperp.logistics.api;

import java.math.BigDecimal;
import java.util.List;

public record CarrierDto(
    long id,
    String code,
    String name,
    List<String> coverageCities,
    boolean national,
    BigDecimal baseRate,
    BigDecimal ratePerKg,
    double onTimeRate,
    boolean active) {

  public boolean covers(String city) {
    return national || coverageCities.stream().anyMatch(c -> c.equalsIgnoreCase(city));
  }
}
