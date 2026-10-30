package com.xperp.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record StockId(
    @Column(name = "product_id") Long productId, @Column(name = "warehouse_id") Long warehouseId)
    implements Serializable {}
