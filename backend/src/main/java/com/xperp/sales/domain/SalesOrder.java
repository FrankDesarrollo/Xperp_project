package com.xperp.sales.domain;

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
@Table(name = "sales_orders")
public class SalesOrder extends TenantEntity {

  @Column(nullable = false, length = 20)
  private String number;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  @Column(name = "warehouse_id", nullable = false)
  private Long warehouseId;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal subtotal = BigDecimal.ZERO;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal discount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;

  @Column(name = "hold_reason", length = 300)
  private String holdReason;

  @Column(nullable = false)
  private boolean flagged;

  @Column(name = "ordered_at", nullable = false)
  private Instant orderedAt;

  @Column(name = "promised_at")
  private Instant promisedAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  private List<SalesOrderItem> items = new ArrayList<>();
}
