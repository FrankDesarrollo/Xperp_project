package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "suppliers")
public class Supplier extends TenantEntity {

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 20)
  private String nit;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "partner_code", nullable = false, length = 10)
  private String partnerCode;

  @Column(name = "min_order_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal minOrderAmount;

  @Column(name = "payment_terms_days", nullable = false)
  private int paymentTermsDays;

  @Column(name = "on_time_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal onTimeRate;

  @Column(name = "fill_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal fillRate;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
