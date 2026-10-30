package com.xperp.inventory.events;

/** Se registró un conteo físico. {@code recount} indica que viene de una tarea de reconteo. */
public record InventoryCountRecorded(
    long companyId,
    long countId,
    long productId,
    long warehouseId,
    int systemQty,
    int countedQty,
    boolean recount) {}
