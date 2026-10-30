package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Hito de la línea de tiempo de una OC. */
@Getter
@Setter
@Entity
@Table(name = "purchase_order_events")
public class PurchaseOrderEvent extends TenantEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "purchase_order_id")
  private PurchaseOrder order;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(length = 300)
  private String note;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
