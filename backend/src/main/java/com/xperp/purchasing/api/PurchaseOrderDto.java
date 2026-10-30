package com.xperp.purchasing.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderDto(
    long id,
    String number,
    long supplierId,
    String supplierCode,
    String supplierName,
    long warehouseId,
    String status,
    String statusReason,
    BigDecimal subtotal,
    BigDecimal total,
    Instant expectedAt,
    Instant firstExpectedAt,
    Instant sentAt,
    Instant confirmedAt,
    Instant receivedAt,
    Instant createdAt,
    Long createdByUserId,
    Long aiDecisionId,
    Long replacesPoId,
    String externalId,
    List<Line> lines,
    List<Event> events) {

  public record Line(
      long id,
      long productId,
      int quantity,
      Integer quantityShipped,
      int quantityReceived,
      BigDecimal unitCost) {

    public BigDecimal lineTotal() {
      return unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    /** Lo que falta por recibir. */
    public int pending() {
      return Math.max(0, quantity - quantityReceived);
    }
  }

  public record Event(String status, String note, Instant occurredAt) {}

  public boolean isOpen() {
    return switch (status) {
      case "draft", "pending_approval", "sent", "confirmed", "delayed", "shipped" -> true;
      default -> false;
    };
  }
}
