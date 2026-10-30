package com.xperp.logistics.api;

import java.math.BigDecimal;
import java.time.Instant;

public record ShipmentDto(
    long id,
    long salesOrderId,
    long carrierId,
    String service,
    String trackingNumber,
    String status,
    BigDecimal cost,
    BigDecimal weightKg,
    String originCity,
    String destinationCity,
    Instant eta,
    Instant shippedAt,
    Instant deliveredAt,
    Long aiDecisionId,
    Instant createdAt) {}
