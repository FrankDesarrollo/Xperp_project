package com.xperp.partners;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuración de partners-mock: token, secreto de webhooks y comportamiento de cada socio. */
@ConfigurationProperties("xperp.partners")
public record PartnersProperties(
    String apiToken,
    String webhookSecret,
    @DefaultValue("http://localhost:8080") String backendUrl,
    @DefaultValue("42") long randomSeed,
    Map<String, Supplier> suppliers,
    Map<String, Carrier> carriers) {

  public record Supplier(
      String city,
      int leadTimeDays,
      double rejectProbability,
      double delayProbability,
      List<Integer> delayDays,
      double partialProbability) {

    /** Días de entrega a una bodega. */
    public int leadTimeTo(String warehouseCode) {
      if ("BOG-01".equals(warehouseCode)) {
        return "Bogotá".equals(city) ? 1 : leadTimeDays + 2;
      }
      return leadTimeDays;
    }
  }

  public record Carrier(
      String name,
      @DefaultValue("false") boolean national,
      List<String> coverage,
      BigDecimal baseRate,
      BigDecimal ratePerKg,
      List<Integer> etaDays,
      double onTimeRate) {

    public boolean covers(String city) {
      return national || coverage.stream().anyMatch(c -> c.equalsIgnoreCase(city));
    }
  }
}
