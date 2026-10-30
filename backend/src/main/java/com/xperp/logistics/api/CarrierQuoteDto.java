package com.xperp.logistics.api;

import java.math.BigDecimal;

/** Cotización de una transportadora para un pedido. */
public record CarrierQuoteDto(CarrierDto carrier, String service, BigDecimal cost, int etaDays) {}
