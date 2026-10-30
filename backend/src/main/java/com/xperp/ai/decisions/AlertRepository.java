package com.xperp.ai.decisions;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

  List<Alert> findAllByOrderByIdDesc(Pageable pageable);

  List<Alert> findByReadFalseOrderByIdDesc(Pageable pageable);
}
