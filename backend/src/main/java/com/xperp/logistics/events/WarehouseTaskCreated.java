package com.xperp.logistics.events;

public record WarehouseTaskCreated(
    long companyId,
    long taskId,
    String type,
    long warehouseId,
    String title,
    Long assignedToUserId) {}
