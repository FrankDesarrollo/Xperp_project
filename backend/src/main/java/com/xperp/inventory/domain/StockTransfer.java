package com.xperp.inventory.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_transfers")
public class StockTransfer extends TenantEntity {

  @Column(nullable = false, length = 20)
  private String number;

  @Column(name = "from_warehouse_id", nullable = false)
  private Long fromWarehouseId;

  @Column(name = "to_warehouse_id", nullable = false)
  private Long toWarehouseId;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "ai_decision_id")
  private Long aiDecisionId;

  @Column(name = "shipped_at")
  private Instant shippedAt;

  @Column(name = "received_at")
  private Instant receivedAt;

  @OneToMany(
      mappedBy = "transfer",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("id")
  private List<StockTransferItem> items = new ArrayList<>();
}
