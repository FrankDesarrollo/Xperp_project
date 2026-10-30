package com.xperp.logistics.domain;

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
@Table(name = "shipments")
public class Shipment extends TenantEntity {

  @Column(name = "sales_order_id", nullable = false)
  private Long salesOrderId;

  @Column(name = "carrier_id", nullable = false)
  private Long carrierId;

  @Column(nullable = false, length = 40)
  private String service;

  @Column(name = "tracking_number", nullable = false, length = 40)
  private String trackingNumber;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal cost;

  @Column(name = "weight_kg", nullable = false, precision = 10, scale = 3)
  private BigDecimal weightKg;

  @Column(name = "origin_city", nullable = false, length = 100)
  private String originCity;

  @Column(name = "destination_city", nullable = false, length = 100)
  private String destinationCity;

  private Instant eta;

  @Column(name = "label_path", length = 300)
  private String labelPath;

  @Column(name = "shipped_at")
  private Instant shippedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "ai_decision_id")
  private Long aiDecisionId;
}
