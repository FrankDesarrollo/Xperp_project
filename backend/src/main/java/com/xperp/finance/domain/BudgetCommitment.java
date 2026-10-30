package com.xperp.finance.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Lo que una OC comprometió en un presupuesto. */
@Getter
@Setter
@Entity
@Table(name = "budget_commitments")
public class BudgetCommitment extends TenantEntity {

  @Column(name = "budget_id", nullable = false)
  private Long budgetId;

  @Column(name = "purchase_order_id", nullable = false)
  private Long purchaseOrderId;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 20)
  private String status;
}
