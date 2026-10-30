package com.xperp.inventory.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Movimiento del kardex. La cantidad lleva signo. */
@Getter
@Setter
@Entity
@Table(name = "inventory_movements")
public class InventoryMovement extends TenantEntity {

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "warehouse_id", nullable = false)
  private Long warehouseId;

  @Column(nullable = false, length = 20)
  private String type;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "balance_after", nullable = false)
  private int balanceAfter;

  @Column(name = "unit_cost", precision = 14, scale = 2)
  private BigDecimal unitCost;

  @Column(name = "reference_type", length = 30)
  private String referenceType;

  @Column(name = "reference_id")
  private Long referenceId;

  @Column(length = 300)
  private String note;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_by", length = 200)
  private String createdBy;
}
