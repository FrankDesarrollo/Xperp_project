package com.xperp.finance.repository;

import com.xperp.finance.domain.AccountPayable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayableRepository extends JpaRepository<AccountPayable, Long> {

  Page<AccountPayable> findByStatus(String status, Pageable pageable);
}
