package com.xperp.sales.domain;

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
@Table(name = "sales_order_items")
public class SalesOrderItem extends TenantEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "sales_order_id")
  private SalesOrder order;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "quantity_fulfilled", nullable = false)
  private int quantityFulfilled;

  @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitPrice;
}
