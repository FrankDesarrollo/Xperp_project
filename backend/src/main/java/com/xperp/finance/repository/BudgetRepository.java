package com.xperp.finance.repository;

import com.xperp.finance.domain.Budget;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

  List<Budget> findByPeriodOrderByCategoryIdAsc(String period);

  Optional<Budget> findByCategoryIdAndPeriod(Long categoryId, String period);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Budget b where b.id = :id")
  Optional<Budget> findForUpdate(@Param("id") Long id);
}
