package com.xperp.inventory.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inventory_counts")
public class InventoryCount extends TenantEntity {

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "warehouse_id", nullable = false)
  private Long warehouseId;

  @Column(name = "system_qty", nullable = false)
  private int systemQty;

  @Column(name = "counted_qty", nullable = false)
  private int countedQty;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "counted_by", length = 200)
  private String countedBy;

  @Column(name = "counted_at", nullable = false)
  private Instant countedAt;
}
