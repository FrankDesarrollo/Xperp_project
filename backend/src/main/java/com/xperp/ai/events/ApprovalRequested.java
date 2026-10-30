package com.xperp.ai.events;

import java.math.BigDecimal;

public record ApprovalRequested(
    long companyId,
    long approvalId,
    long decisionId,
    String requestedRole,
    String title,
    BigDecimal amount) {}
