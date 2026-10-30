package com.xperp.purchasing.domain;

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
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem extends TenantEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "goods_receipt_id")
  private GoodsReceipt receipt;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "quantity_received", nullable = false)
  private int quantityReceived;
}
