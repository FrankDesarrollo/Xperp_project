package com.xperp.ai.decisions;

import com.xperp.ai.events.AiDecisionRecorded;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.PolicyEngine;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe la bitácora {@code ai_decisions} (AGENTS.md, regla 6: toda acción queda registrada,
 * también si quedó bloqueada). Cada método corre en su propia transacción para que la decisión
 * quede guardada aunque la ejecución falle.
 */
@Component
public class DecisionRecorder {

  public static final String STATUS_EXECUTING = "executing";

  private final AiDecisionRepository decisions;
  private final AiDecisionRefRepository refs;
  private final ApplicationEventPublisher events;
  private final SseHub sse;
  private final Clock clock;

  DecisionRecorder(
      AiDecisionRepository decisions,
      AiDecisionRefRepository refs,
      ApplicationEventPublisher events,
      SseHub sse,
      Clock clock) {
    this.decisions = decisions;
    this.refs = refs;
    this.events = events;
    this.sse = sse;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long create(ActionProposal p, PolicyEngine.PolicyDecision policy, String status) {
    AiDecision d = new AiDecision();
    d.setDecisionType(p.decisionType());
    d.setActionType(p.actionType());
    d.setTitle(truncate(p.title(), 200));
    d.setTriggerEvent(p.triggerEvent());
    d.setTriggerRef(truncate(p.triggerRef(), 100));
    d.setInputSnapshot(
        p.inputSnapshot() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(p.inputSnapshot()));
    d.setAlternatives(
        p.alternatives() == null ? new ArrayList<>() : new ArrayList<>(p.alternatives()));
    d.setProposedAction(
        p.payload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(p.payload()));
    d.setReasoning(p.reasoning() == null || p.reasoning().isBlank() ? "—" : p.reasoning());
    d.setConfidence(
        BigDecimal.valueOf(Math.max(0, Math.min(1, p.confidence())))
            .setScale(3, java.math.RoundingMode.HALF_UP));
    d.setAmount(p.amount());
    d.setPolicyResult(policy.result());
    d.setPolicyReason(truncate(policy.reason(), 300));
    d.setAutonomyLevel(policy.level());
    d.setStatus(status);
    d.setLlmUsed(p.llm().used());
    d.setLlmModel(p.llm().model());
    d.setPromptVersion(p.llm().promptVersion());
    d.setTokensIn(p.llm().tokensIn());
    d.setTokensOut(p.llm().tokensOut());
    d.setLatencyMs(p.llm().latencyMs());
    d.setSimTime(Instant.now(clock));
    decisions.save(d);
    saveRefs(d.getId(), p.references());
    return d.getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finish(
      long id,
      String status,
      String message,
      Map<String, Object> resultRef,
      List<ActionProposal.Ref> newRefs,
      boolean notifyUsers) {
    AiDecision d = decisions.findById(id).orElseThrow();
    d.setStatus(status);
    if (message != null) {
      d.setResultMessage(truncate(message, 500));
    }
    if (resultRef != null && !resultRef.isEmpty()) {
      d.setResultRef(new LinkedHashMap<>(resultRef));
    }
    if ("executed".equals(status) || "approved".equals(status)) {
      d.setExecutedAt(Instant.now(clock));
    }
    saveRefs(id, newRefs);
    publish(d, notifyUsers);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markReverted(long id, String message) {
    AiDecision d = decisions.findById(id).orElseThrow();
    d.setStatus("reverted");
    d.setRevertedAt(Instant.now(clock));
    d.setResultMessage(truncate(message, 500));
    publish(d, true);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markApproved(long id, Long approvedBy) {
    AiDecision d = decisions.findById(id).orElseThrow();
    d.setApprovedBy(approvedBy);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void appendReasoning(long id, String text) {
    AiDecision d = decisions.findById(id).orElseThrow();
    d.setReasoning(d.getReasoning() + " " + text);
  }

  private void saveRefs(long decisionId, List<ActionProposal.Ref> list) {
    if (list == null) {
      return;
    }
    for (ActionProposal.Ref r : list) {
      AiDecisionRef ref = new AiDecisionRef();
      ref.setAiDecisionId(decisionId);
      ref.setRefType(r.type());
      ref.setRefId(r.id());
      ref.setLabel(truncate(r.label(), 100));
      refs.save(ref);
    }
  }

  private void publish(AiDecision d, boolean notifyUsers) {
    events.publishEvent(
        new AiDecisionRecorded(
            TenantContext.require(),
            d.getId(),
            d.getDecisionType(),
            d.getActionType(),
            d.getTitle(),
            d.getPolicyResult(),
            d.getStatus(),
            notifyUsers,
            truncate(d.getReasoning(), 300)));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", d.getId());
    data.put("decision_type", d.getDecisionType());
    data.put("title", d.getTitle());
    data.put("status", d.getStatus());
    data.put("policy_result", d.getPolicyResult());
    sse.publish("ai.decision", data);
  }

  static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() > max ? s.substring(0, max - 3) + "..." : s;
  }
}
