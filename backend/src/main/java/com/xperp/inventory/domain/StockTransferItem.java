package com.xperp.inventory.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_transfer_items")
public class StockTransferItem extends TenantEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "stock_transfer_id")
  private StockTransfer transfer;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(nullable = false)
  private int quantity;
}
