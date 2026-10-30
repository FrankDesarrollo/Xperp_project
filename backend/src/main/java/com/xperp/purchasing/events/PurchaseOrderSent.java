package com.xperp.purchasing.events;

import java.math.BigDecimal;
import java.util.Map;

/** OC enviada al proveedor. Finanzas compromete presupuesto por categoría. */
public record PurchaseOrderSent(
    long companyId,
    long purchaseOrderId,
    String number,
    long supplierId,
    long warehouseId,
    BigDecimal total,
    Map<Long, BigDecimal> amountsByCategory,
    String period,
    boolean createdByAi) {}
