package com.xperp.ai.events;

public record AlertRaised(
    long companyId, long alertId, String type, String severity, String message) {}
