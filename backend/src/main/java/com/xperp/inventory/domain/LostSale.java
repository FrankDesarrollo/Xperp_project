package com.xperp.inventory.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Demanda no atendida por falta de disponible (KPI de nivel de servicio). */
@Getter
@Setter
@Entity
@Table(name = "lost_sales")
public class LostSale extends TenantEntity {

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "warehouse_id", nullable = false)
  private Long warehouseId;

  @Column(name = "sales_order_id")
  private Long salesOrderId;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
