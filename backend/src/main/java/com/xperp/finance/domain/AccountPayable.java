package com.xperp.finance.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "accounts_payable")
public class AccountPayable extends TenantEntity {

  @Column(name = "purchase_order_id", nullable = false)
  private Long purchaseOrderId;

  @Column(name = "supplier_id", nullable = false)
  private Long supplierId;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(nullable = false, length = 20)
  private String status;
}
