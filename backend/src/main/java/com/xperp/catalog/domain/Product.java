package com.xperp.catalog.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends TenantEntity {

  @Column(nullable = false, length = 20)
  private String sku;

  @Column(nullable = false, length = 200)
  private String name;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "category_id")
  private Category category;

  @Column(nullable = false, length = 10)
  private String unit;

  @Column(name = "units_per_case", nullable = false)
  private int unitsPerCase;

  @Column(name = "weight_kg", nullable = false, precision = 8, scale = 3)
  private BigDecimal weightKg;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal cost;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private boolean perishable;

  @Column(name = "shelf_life_days")
  private Integer shelfLifeDays;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
