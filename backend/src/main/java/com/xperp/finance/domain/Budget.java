package com.xperp.finance.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "budgets")
public class Budget extends TenantEntity {

  @Column(name = "category_id", nullable = false)
  private Long categoryId;

  @Column(nullable = false, length = 7)
  private String period;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal committed = BigDecimal.ZERO;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal spent = BigDecimal.ZERO;

  @Version private long version;
}
