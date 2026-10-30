package com.xperp.inventory.api;

import java.math.BigDecimal;

/** Parámetros de reabastecimiento que calcula la IA (AGENTS.md, sección 8.1). */
public record StockParams(
    int safetyStock, int reorderPoint, int maxStock, int coverDays, BigDecimal avgDailyDemand) {}
