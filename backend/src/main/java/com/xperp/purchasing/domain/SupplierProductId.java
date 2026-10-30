package com.xperp.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record SupplierProductId(
    @Column(name = "supplier_id") Long supplierId, @Column(name = "product_id") Long productId)
    implements Serializable {}
