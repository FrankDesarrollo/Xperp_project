package com.xperp.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record SupplierWarehouseId(
    @Column(name = "supplier_id") Long supplierId, @Column(name = "warehouse_id") Long warehouseId)
    implements Serializable {}
