package com.xperp.inventory.domain;

import com.xperp.shared.persistence.TimestampedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Existencia de un SKU en una bodega (tabla inventory). */
@Getter
@Setter
@Entity
@Table(name = "inventory")
public class Stock extends TimestampedTenantEntity {

  @EmbeddedId private StockId id;

  @Column(name = "on_hand", nullable = false)
  private int onHand;

  @Column(nullable = false)
  private int reserved;

  @Column(name = "safety_stock", nullable = false)
  private int safetyStock;

  @Column(name = "reorder_point", nullable = false)
  private int reorderPoint;

  @Column(name = "cover_days", nullable = false)
  private int coverDays = 10;

  @Column(name = "max_stock", nullable = false)
  private int maxStock;

  @Column(name = "avg_daily_demand", nullable = false, precision = 12, scale = 2)
  private BigDecimal avgDailyDemand = BigDecimal.ZERO;

  @Column(name = "promotion_suggested", nullable = false)
  private boolean promotionSuggested;

  @Column(name = "reorder_blocked", nullable = false)
  private boolean reorderBlocked;

  @Column(name = "params_updated_at")
  private Instant paramsUpdatedAt;

  @Version private long version;

  public int available() {
    return onHand - reserved;
  }
}
