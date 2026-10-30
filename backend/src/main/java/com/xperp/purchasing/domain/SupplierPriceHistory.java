package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "supplier_price_history")
public class SupplierPriceHistory extends TenantEntity {

  @Column(name = "supplier_id", nullable = false)
  private Long supplierId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitCost;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;
}
