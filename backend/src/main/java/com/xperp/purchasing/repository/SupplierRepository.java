package com.xperp.purchasing.repository;

import com.xperp.purchasing.domain.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

  List<Supplier> findAllByOrderByCodeAsc();

  Optional<Supplier> findByCode(String code);
}
