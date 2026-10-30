package com.xperp.inventory.repository;

import com.xperp.inventory.domain.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementRepository extends JpaRepository<InventoryMovement, Long> {

  Page<InventoryMovement> findByProductIdAndWarehouseIdOrderByOccurredAtDescIdDesc(
      Long productId, Long warehouseId, Pageable pageable);
}
