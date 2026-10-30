package com.xperp.ai.policies;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutonomyPolicyRepository extends JpaRepository<AutonomyPolicy, Long> {

  List<AutonomyPolicy> findAllByOrderByActionTypeAscMinAmountAscIdAsc();

  List<AutonomyPolicy> findByActionTypeAndEnabledTrue(String actionType);
}
