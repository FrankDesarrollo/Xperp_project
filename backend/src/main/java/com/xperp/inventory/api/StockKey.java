package com.xperp.inventory.api;

/** Llave de una existencia: SKU en una bodega. */
public record StockKey(long productId, long warehouseId) {}
