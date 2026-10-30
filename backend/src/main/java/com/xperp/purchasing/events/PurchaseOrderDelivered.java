package com.xperp.purchasing.events;

/** El proveedor entregó la mercancía en la bodega; falta registrar la recepción. */
public record PurchaseOrderDelivered(
    long companyId, long purchaseOrderId, String number, long warehouseId) {}
