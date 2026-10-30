package com.xperp.ai.events;

/** La IA registró una decisión. notify indica si hay que avisar a las personas (nivel 2). */
public record AiDecisionRecorded(
    long companyId,
    long decisionId,
    String decisionType,
    String actionType,
    String title,
    String policyResult,
    String status,
    boolean notifyUsers,
    String summary) {}
