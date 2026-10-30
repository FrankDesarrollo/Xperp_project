package com.xperp.sales.events;

import java.math.BigDecimal;

/** Pedido creado. {@code status} es created, on_hold (retenido) o cancelled (sin existencias). */
public record SalesOrderCreated(
    long companyId,
    long salesOrderId,
    String number,
    long warehouseId,
    String status,
    BigDecimal total,
    String holdReason) {}
