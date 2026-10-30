package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem extends TenantEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "purchase_order_id")
  private PurchaseOrder order;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "quantity_shipped")
  private Integer quantityShipped;

  @Column(name = "quantity_received", nullable = false)
  private int quantityReceived;

  @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitCost;
}
