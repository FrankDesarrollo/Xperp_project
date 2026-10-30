package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt extends TenantEntity {

  @Column(name = "purchase_order_id", nullable = false)
  private Long purchaseOrderId;

  @Column(name = "received_by", length = 200)
  private String receivedBy;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<GoodsReceiptItem> items = new ArrayList<>();
}
