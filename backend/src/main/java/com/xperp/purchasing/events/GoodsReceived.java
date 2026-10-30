package com.xperp.purchasing.events;

import java.math.BigDecimal;
import java.util.Map;

/** Se recibió mercancía. Finanzas pasa el compromiso a gastado y crea la cuenta por pagar. */
public record GoodsReceived(
    long companyId,
    long purchaseOrderId,
    String number,
    long supplierId,
    int paymentTermsDays,
    BigDecimal total,
    Map<Long, BigDecimal> amountsByCategory) {}
