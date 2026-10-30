package com.xperp.inventory.repository;

import com.xperp.inventory.domain.Warehouse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

  List<Warehouse> findAllByOrderByIdAsc();

  Optional<Warehouse> findByCode(String code);
}
