package com.xperp.inventory.repository;

import com.xperp.inventory.domain.InventoryCount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountRepository extends JpaRepository<InventoryCount, Long> {

  List<InventoryCount> findTop50ByOrderByCountedAtDescIdDesc();

  List<InventoryCount> findTop50ByWarehouseIdOrderByCountedAtDescIdDesc(Long warehouseId);
}
