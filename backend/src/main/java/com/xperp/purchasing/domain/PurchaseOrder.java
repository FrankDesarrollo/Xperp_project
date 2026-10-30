package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends TenantEntity {

  @Column(nullable = false, length = 20)
  private String number;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "supplier_id")
  private Supplier supplier;

  @Column(name = "warehouse_id", nullable = false)
  private Long warehouseId;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "status_reason", length = 300)
  private String statusReason;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal subtotal = BigDecimal.ZERO;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;

  @Column(name = "expected_at")
  private Instant expectedAt;

  @Column(name = "first_expected_at")
  private Instant firstExpectedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "received_at")
  private Instant receivedAt;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @Column(name = "ai_decision_id")
  private Long aiDecisionId;

  @Column(name = "replaces_po_id")
  private Long replacesPoId;

  @Column(name = "idempotency_key", length = 64)
  private String idempotencyKey;

  @Column(name = "external_id", length = 40)
  private String externalId;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  private List<PurchaseOrderItem> items = new ArrayList<>();

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("occurredAt, id")
  private List<PurchaseOrderEvent> events = new ArrayList<>();
}
