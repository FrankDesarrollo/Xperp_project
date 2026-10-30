package com.xperp.purchasing.repository;

import com.xperp.purchasing.domain.SupplierProduct;
import com.xperp.purchasing.domain.SupplierProductId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierProductRepository
    extends JpaRepository<SupplierProduct, SupplierProductId> {

  List<SupplierProduct> findByIdProductId(Long productId);

  List<SupplierProduct> findByIdSupplierId(Long supplierId);
}
