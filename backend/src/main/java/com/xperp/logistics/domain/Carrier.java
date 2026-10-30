package com.xperp.logistics.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "carriers")
public class Carrier extends TenantEntity {

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "partner_code", nullable = false, length = 10)
  private String partnerCode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "coverage_cities", nullable = false, columnDefinition = "jsonb")
  private List<String> coverageCities = new ArrayList<>();

  @Column(nullable = false)
  private boolean national;

  @Column(name = "base_rate", nullable = false, precision = 14, scale = 2)
  private BigDecimal baseRate;

  @Column(name = "rate_per_kg", nullable = false, precision = 14, scale = 2)
  private BigDecimal ratePerKg;

  @Column(name = "on_time_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal onTimeRate;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
