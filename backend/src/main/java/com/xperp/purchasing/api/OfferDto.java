package com.xperp.purchasing.api;

import java.math.BigDecimal;
import java.time.Instant;

/** Oferta de un proveedor para un producto, con los datos del proveedor que pesan en el puntaje. */
public record OfferDto(
    SupplierDto supplier,
    long productId,
    BigDecimal unitCost,
    int minOrderCases,
    boolean preferred,
    Instant priceUpdatedAt) {}
