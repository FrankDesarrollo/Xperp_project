package com.xperp.purchasing.events;

import java.time.Instant;

/** Cambió el estado de una OC (respuesta del proveedor, cancelación o recepción). */
public record PurchaseOrderStatusChanged(
    long companyId,
    long purchaseOrderId,
    String number,
    long supplierId,
    long warehouseId,
    String previousStatus,
    String status,
    String reason,
    Instant expectedAt) {}
