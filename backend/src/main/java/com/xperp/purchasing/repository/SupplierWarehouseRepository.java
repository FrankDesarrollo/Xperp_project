package com.xperp.purchasing.repository;

import com.xperp.purchasing.domain.SupplierWarehouse;
import com.xperp.purchasing.domain.SupplierWarehouseId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierWarehouseRepository
    extends JpaRepository<SupplierWarehouse, SupplierWarehouseId> {}
