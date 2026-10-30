package com.xperp.inventory.api;

import java.time.Instant;
import java.util.List;

public record TransferDto(
    long id,
    String number,
    long fromWarehouseId,
    long toWarehouseId,
    String status,
    Long aiDecisionId,
    Instant shippedAt,
    Instant receivedAt,
    Instant createdAt,
    List<TransferLine> lines) {}
