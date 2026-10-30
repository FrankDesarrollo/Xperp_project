package com.xperp.ai.decisions;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

  @Query(
      """
      select a from Approval a
      where (:status is null or a.status = :status)
        and a.requestedRole in :roles
      """)
  Page<Approval> search(
      @Param("status") String status, @Param("roles") Collection<String> roles, Pageable pageable);

  List<Approval> findByStatusAndExpiresAtBefore(String status, Instant now);

  List<Approval> findByStatus(String status);

  Optional<Approval> findByAiDecisionId(Long aiDecisionId);
}
