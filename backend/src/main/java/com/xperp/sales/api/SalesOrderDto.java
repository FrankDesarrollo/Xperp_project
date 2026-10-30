package com.xperp.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SalesOrderDto(
    long id,
    String number,
    long customerId,
    String customerName,
    String customerSegment,
    String customerCity,
    long warehouseId,
    String status,
    BigDecimal subtotal,
    BigDecimal discount,
    BigDecimal total,
    String holdReason,
    boolean flagged,
    Instant orderedAt,
    Instant promisedAt,
    List<SalesOrderLineDto> lines) {}
