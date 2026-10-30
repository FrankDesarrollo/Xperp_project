package com.xperp.sales.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends TenantEntity {

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 30)
  private String document;

  @Column(name = "segment_code", nullable = false, length = 20)
  private String segmentCode;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(length = 200)
  private String address;

  @Column(name = "warehouse_id", nullable = false)
  private Long warehouseId;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
