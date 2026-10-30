package com.xperp.ai.events;

public record ApprovalResolved(
    long companyId,
    long approvalId,
    long decisionId,
    String status,
    String title,
    Long decidedBy) {}
