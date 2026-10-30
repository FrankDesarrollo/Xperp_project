package com.xperp.purchasing.domain;

import com.xperp.shared.persistence.TimestampedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Tiempo de entrega de un proveedor a una bodega. */
@Getter
@Setter
@Entity
@Table(name = "supplier_warehouses")
public class SupplierWarehouse extends TimestampedTenantEntity {

  @EmbeddedId private SupplierWarehouseId id;

  @Column(name = "lead_time_days", nullable = false)
  private int leadTimeDays;
}
