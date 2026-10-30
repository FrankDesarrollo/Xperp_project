package com.xperp.finance.repository;

import com.xperp.finance.domain.BudgetCommitment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitmentRepository extends JpaRepository<BudgetCommitment, Long> {

  List<BudgetCommitment> findByPurchaseOrderIdAndStatus(Long purchaseOrderId, String status);
}
