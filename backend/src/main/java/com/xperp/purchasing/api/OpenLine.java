package com.xperp.purchasing.api;

import java.time.Instant;

/** Unidades pendientes de una OC abierta. */
public record OpenLine(
    long purchaseOrderId,
    String number,
    long supplierId,
    long warehouseId,
    long productId,
    int pendingQuantity,
    String status,
    Instant expectedAt) {}
