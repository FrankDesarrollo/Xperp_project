package com.xperp.sales.api;

/** Cuántas líneas tiene un cliente con un SKU y su cantidad promedio. */
public record CustomerSkuStats(long productId, int lines, double avgQuantity, int maxQuantity) {}
