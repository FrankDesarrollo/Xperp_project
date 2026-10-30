package com.xperp.partners.api;

import java.math.BigDecimal;
import java.util.Optional;

/** Transportadoras simuladas (contracts/partners.yaml). */
public interface CarrierGateway {

  record Quote(String carrierCode, String service, BigDecimal cost, int etaDays) {}

  /** Cotiza; vacío si la transportadora no cubre la ciudad. */
  Optional<Quote> quote(
      String carrierCode, String originCity, String destinationCity, double weightKg);

  /** Crea el envío y devuelve el número de guía. */
  String createShipment(
      String carrierCode,
      String salesOrderNumber,
      String originCity,
      String destinationCity,
      double weightKg,
      String service);
}
