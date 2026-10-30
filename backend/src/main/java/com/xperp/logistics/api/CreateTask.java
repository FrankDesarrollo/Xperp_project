package com.xperp.logistics.api;

import java.time.Instant;

/** Datos de una tarea nueva. Las referencias que no apliquen van en null. */
public record CreateTask(
    long warehouseId,
    String type,
    int priority,
    String title,
    String detail,
    Long salesOrderId,
    Long purchaseOrderId,
    Long stockTransferId,
    Long productId,
    String referenceNumber,
    Instant dueAt,
    Long aiDecisionId) {}
