package com.xperp.inventory.events;

/** El disponible de un SKU bajó del stock de seguridad. Dispara una revisión urgente. */
public record StockBelowSafety(
    long companyId, long productId, long warehouseId, int available, int safetyStock) {}
