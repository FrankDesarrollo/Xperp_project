package com.xperp.catalog.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category extends TenantEntity {

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;
}
