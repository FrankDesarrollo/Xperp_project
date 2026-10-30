package com.xperp.purchasing.api;

import java.math.BigDecimal;

/** Línea de una OC nueva. Si unitCost es null se usa el costo de la oferta del proveedor. */
public record PurchaseLine(long productId, int quantity, BigDecimal unitCost) {}
