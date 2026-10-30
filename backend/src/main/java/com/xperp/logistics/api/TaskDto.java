package com.xperp.logistics.api;

import java.time.Instant;

public record TaskDto(
    long id,
    long warehouseId,
    String type,
    String status,
    int priority,
    String title,
    String detail,
    Long salesOrderId,
    Long purchaseOrderId,
    Long stockTransferId,
    Long productId,
    String referenceNumber,
    Long dependsOnTaskId,
    Long assignedToUserId,
    boolean handledByHuman,
    Instant dueAt,
    Instant startedAt,
    Instant completedAt,
    Long completedBy,
    Long aiDecisionId,
    Instant createdAt) {

  public boolean isOpen() {
    return "pending".equals(status) || "in_progress".equals(status);
  }
}
