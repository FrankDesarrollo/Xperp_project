package com.xperp.ai.decisions;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiDecisionRepository extends JpaRepository<AiDecision, Long> {

  @Query(
      """
      select d from AiDecision d
      where (:type is null or d.decisionType = :type)
        and (:status is null or d.status = :status)
        and (:exclude is null or d.decisionType <> :exclude)
      """)
  Page<AiDecision> search(
      @Param("type") String type,
      @Param("status") String status,
      @Param("exclude") String exclude,
      Pageable pageable);

  @Query(
      """
      select d from AiDecision d
      where d.id in (select r.aiDecisionId from AiDecisionRef r
                     where r.refType = :refType and r.refId = :refId)
      """)
  Page<AiDecision> byReference(
      @Param("refType") String refType, @Param("refId") Long refId, Pageable pageable);

  boolean existsByTriggerRefAndStatusIn(String triggerRef, Collection<String> statuses);

  boolean existsByDecisionTypeAndTriggerRefAndSimTimeAfter(
      String decisionType, String triggerRef, Instant after);

  List<AiDecision> findBySimTimeBetween(Instant from, Instant to);

  List<AiDecision> findTop20ByTitleContainingIgnoreCaseOrderByIdDesc(String text);
}
