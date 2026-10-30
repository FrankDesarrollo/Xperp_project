package com.xperp.ai.decisions;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiDecisionRefRepository extends JpaRepository<AiDecisionRef, Long> {

  List<AiDecisionRef> findByAiDecisionIdIn(Collection<Long> decisionIds);
}
