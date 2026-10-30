package com.xperp.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayableDto(
    long id,
    long purchaseOrderId,
    long supplierId,
    BigDecimal amount,
    LocalDate dueDate,
    String status) {}
