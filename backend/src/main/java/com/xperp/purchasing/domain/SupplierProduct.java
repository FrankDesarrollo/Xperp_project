package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TimestampedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Oferta: lo que cobra un proveedor por un producto. */
@Getter
@Setter
@Entity
@Table(name = "supplier_products")
public class SupplierProduct extends TimestampedTenantEntity {

  @EmbeddedId private SupplierProductId id;

  @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitCost;

  @Column(name = "min_order_cases", nullable = false)
  private int minOrderCases;

  @Column(name = "is_preferred", nullable = false)
  private boolean preferred;

  @Column(name = "price_updated_at")
  private Instant priceUpdatedAt;
}
