package com.xperp.logistics.events;

public record WarehouseTaskCompleted(
    long companyId,
    long taskId,
    String type,
    long warehouseId,
    Long salesOrderId,
    Long purchaseOrderId,
    Long stockTransferId,
    boolean byHuman) {}
