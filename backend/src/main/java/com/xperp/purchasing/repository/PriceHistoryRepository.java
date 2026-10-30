package com.xperp.purchasing.repository;

import com.xperp.purchasing.domain.SupplierPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<SupplierPriceHistory, Long> {}
