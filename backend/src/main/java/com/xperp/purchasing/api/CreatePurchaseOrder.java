package com.xperp.purchasing.api;

import java.util.List;

/**
 * Datos de una OC nueva. pendingApproval la deja en pending_approval en vez de draft.
 * createdByUserId es null cuando la crea la IA.
 */
public record CreatePurchaseOrder(
    long supplierId,
    long warehouseId,
    List<PurchaseLine> lines,
    Long createdByUserId,
    Long aiDecisionId,
    Long replacesPoId,
    String idempotencyKey,
    boolean pendingApproval,
    String note) {}
