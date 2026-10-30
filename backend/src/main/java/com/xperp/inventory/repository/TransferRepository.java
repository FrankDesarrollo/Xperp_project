package com.xperp.inventory.repository;

import com.xperp.inventory.domain.StockTransfer;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<StockTransfer, Long> {

  Page<StockTransfer> findByStatus(String status, Pageable pageable);

  List<StockTransfer> findByStatusIn(Collection<String> statuses);
}
