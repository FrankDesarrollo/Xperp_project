package com.xperp.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;

public record MovementDto(
    long id,
    long productId,
    long warehouseId,
    String type,
    int quantity,
    int balanceAfter,
    BigDecimal unitCost,
    String referenceType,
    Long referenceId,
    String note,
    Instant occurredAt,
    String createdBy) {}
