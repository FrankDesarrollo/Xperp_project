package com.xperp.ai.decisions;

import com.xperp.ai.policies.ActionExecutor;
import com.xperp.ai.policies.ExecutorRegistry;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta y reversión de la bitácora de decisiones. */
@Service
public class DecisionService {

  static final Set<String> REVERSIBLE_STATUSES = Set.of("executed", "approved", "pending_approval");

  /** Decisión con sus referencias y si todavía se puede revertir. */
  public record View(
      AiDecision decision, List<AiDecisionRef> refs, boolean reversible, Long approvalId) {}

  private final AiDecisionRepository decisions;
  private final AiDecisionRefRepository refs;
  private final ApprovalRepository approvals;
  private final ExecutorRegistry executors;
  private final DecisionRecorder recorder;

  DecisionService(
      AiDecisionRepository decisions,
      AiDecisionRefRepository refs,
      ApprovalRepository approvals,
      ExecutorRegistry executors,
      DecisionRecorder recorder) {
    this.decisions = decisions;
    this.refs = refs;
    this.approvals = approvals;
    this.executors = executors;
    this.recorder = recorder;
  }

  @Transactional(readOnly = true)
  public PageResult<View> list(
      String type,
      String status,
      String exclude,
      String refType,
      Long refId,
      Integer page,
      Integer size) {
    var pageable = PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
    Page<AiDecision> result =
        refType != null && refId != null
            ? decisions.byReference(refType, refId, pageable)
            : decisions.search(blank(type), blank(status), blank(exclude), pageable);
    Map<Long, List<AiDecisionRef>> byDecision =
        refs
            .findByAiDecisionIdIn(result.getContent().stream().map(AiDecision::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(AiDecisionRef::getAiDecisionId));
    return PageResult.of(result, d -> view(d, byDecision.getOrDefault(d.getId(), List.of())));
  }

  @Transactional(readOnly = true)
  public View get(long id) {
    AiDecision d =
        decisions.findById(id).orElseThrow(() -> BusinessException.notFound("Decisión", id));
    return view(d, refs.findByAiDecisionIdIn(List.of(id)));
  }

  /** Revierte una decisión ejecutada mientras sea reversible (AGENTS.md, 9.3). */
  public View revert(long id, String reason) {
    View v = get(id);
    if (!v.reversible()) {
      throw BusinessException.conflict("not_reversible", "Esta decisión ya no se puede revertir");
    }
    AiDecision d = v.decision();
    ActionExecutor executor = executors.get(d.getActionType());
    String message = executor.revert(d.getId(), d.getProposedAction(), resultRef(d));
    recorder.markReverted(
        d.getId(), message + (reason == null || reason.isBlank() ? "" : ". " + reason));
    return get(id);
  }

  private View view(AiDecision d, List<AiDecisionRef> list) {
    boolean reversible = false;
    if (d.getActionType() != null && Set.of("executed", "approved").contains(d.getStatus())) {
      try {
        reversible =
            executors.get(d.getActionType()).reversible(d.getProposedAction(), resultRef(d));
      } catch (RuntimeException e) {
        reversible = false;
      }
    }
    Long approvalId = approvals.findByAiDecisionId(d.getId()).map(Approval::getId).orElse(null);
    return new View(d, list, reversible, approvalId);
  }

  private static Map<String, Object> resultRef(AiDecision d) {
    return d.getResultRef() == null ? Map.of() : d.getResultRef();
  }

  private static String blank(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
