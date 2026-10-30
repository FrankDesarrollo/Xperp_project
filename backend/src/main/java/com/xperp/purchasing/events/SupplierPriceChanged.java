package com.xperp.purchasing.events;

import java.math.BigDecimal;

/** Un proveedor cambió el precio de un producto. */
public record SupplierPriceChanged(
    long companyId,
    long supplierId,
    long productId,
    BigDecimal previousCost,
    BigDecimal newCost,
    double changePct) {}
