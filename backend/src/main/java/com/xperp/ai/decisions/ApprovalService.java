package com.xperp.ai.decisions;

import com.xperp.ai.events.ApprovalRequested;
import com.xperp.ai.events.ApprovalResolved;
import com.xperp.ai.policies.ActionExecutor;
import com.xperp.ai.policies.AiUsageService;
import com.xperp.ai.policies.ExecutorRegistry;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.AuthenticatedUser;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aprobaciones de la IA (AGENTS.md, 9.1 y 9.3): se crean con la decisión, se aprueban o rechazan
 * desde la bandeja y vencen a las 24 horas simuladas.
 */
@Service
public class ApprovalService {

  private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
  public static final Duration TTL = Duration.ofHours(24);

  /** Quién puede decidir cada rol solicitado (admin puede todo). */
  static final Map<String, Set<String>> DECIDERS =
      Map.of(
          "purchasing", Set.of("purchasing", "manager", "admin"),
          "manager", Set.of("manager", "admin"),
          "admin", Set.of("admin"));

  private final ApprovalRepository approvals;
  private final AiDecisionRepository decisions;
  private final DecisionRecorder recorder;
  private final ExecutorRegistry executors;
  private final AiUsageService usage;
  private final ApplicationEventPublisher events;
  private final SseHub sse;
  private final Clock clock;
  private final TransactionTemplate tx;

  static final Set<String> SPENDING = Set.of("create_purchase_order", "reassign_purchase_order");

  ApprovalService(
      ApprovalRepository approvals,
      AiDecisionRepository decisions,
      DecisionRecorder recorder,
      ExecutorRegistry executors,
      AiUsageService usage,
      ApplicationEventPublisher events,
      SseHub sse,
      Clock clock,
      PlatformTransactionManager txManager) {
    this.approvals = approvals;
    this.decisions = decisions;
    this.recorder = recorder;
    this.executors = executors;
    this.usage = usage;
    this.events = events;
    this.sse = sse;
    this.clock = clock;
    this.tx = new TransactionTemplate(txManager);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long request(long decisionId, String role, String title, BigDecimal amount) {
    Instant now = Instant.now(clock);
    Approval a = new Approval();
    a.setAiDecisionId(decisionId);
    a.setRequestedRole(role);
    a.setStatus("pending");
    a.setRequestedAt(now);
    a.setExpiresAt(now.plus(TTL));
    approvals.save(a);
    events.publishEvent(
        new ApprovalRequested(TenantContext.require(), a.getId(), decisionId, role, title, amount));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", a.getId());
    data.put("ai_decision_id", decisionId);
    data.put("requested_role", role);
    data.put("title", title);
    sse.publish("approval.requested", data);
    return a.getId();
  }

  @Transactional(readOnly = true)
  public PageResult<Approval> list(String status, Set<String> roles, Integer page, Integer size) {
    String st = status == null || status.isBlank() ? null : status;
    return PageResult.of(
        approvals.search(
            st, roles, PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"))),
        a -> a);
  }

  @Transactional(readOnly = true)
  public Approval get(long id) {
    return approvals.findById(id).orElseThrow(() -> BusinessException.notFound("Aprobación", id));
  }

  public static boolean canDecide(AuthenticatedUser user, String requestedRole) {
    return DECIDERS.getOrDefault(requestedRole, Set.of("admin")).contains(user.role());
  }

  /** Aprueba y ejecuta la acción. Lo llama una persona o el aprobador virtual. */
  public Approval approve(long id, Long userId, String role, String comment) {
    Approval a = claim(id, userId, role, "approved", comment);
    AiDecision d = decisions.findById(a.getAiDecisionId()).orElseThrow();
    ActionExecutor executor = executors.get(d.getActionType());
    recorder.markApproved(d.getId(), userId);
    try {
      ActionExecutor.Result result =
          executor.executeApproved(d.getId(), d.getProposedAction(), resultRef(d));
      if (d.getAmount() != null && SPENDING.contains(d.getActionType())) {
        usage.addSpend(SimTime.today(clock), d.getAmount());
      }
      Map<String, Object> ref = new LinkedHashMap<>(resultRef(d));
      ref.putAll(result.resultRef());
      recorder.finish(d.getId(), "approved", result.message(), ref, result.refs(), false);
    } catch (RuntimeException e) {
      log.warn("Falló la ejecución aprobada de la decisión {}: {}", d.getId(), e.getMessage());
      recorder.finish(
          d.getId(),
          "failed",
          "Aprobada, pero falló al ejecutar: " + e.getMessage(),
          null,
          List.of(),
          true);
    }
    resolved(a, d);
    return get(id);
  }

  public Approval reject(long id, Long userId, String role, String comment) {
    Approval a = claim(id, userId, role, "rejected", comment);
    AiDecision d = decisions.findById(a.getAiDecisionId()).orElseThrow();
    discard(d);
    recorder.finish(
        d.getId(),
        "rejected",
        comment == null || comment.isBlank() ? "Rechazada" : "Rechazada: " + comment,
        null,
        List.of(),
        false);
    resolved(a, d);
    return get(id);
  }

  /** Vence las aprobaciones pendientes de más de 24 horas simuladas. */
  public int expire() {
    int count = 0;
    List<Approval> due =
        tx.execute(s -> approvals.findByStatusAndExpiresAtBefore("pending", Instant.now(clock)));
    for (Approval pending : due) {
      Approval a = claim(pending.getId(), null, "admin", "expired", "Venció sin respuesta");
      AiDecision d = decisions.findById(a.getAiDecisionId()).orElseThrow();
      discard(d);
      recorder.finish(
          d.getId(), "expired", "La aprobación venció a las 24 horas", null, List.of(), true);
      resolved(a, d);
      count++;
    }
    return count;
  }

  @Transactional(readOnly = true)
  public List<Approval> pending() {
    return approvals.findByStatus("pending");
  }

  private void discard(AiDecision d) {
    try {
      executors.get(d.getActionType()).discard(d.getId(), d.getProposedAction(), resultRef(d));
    } catch (RuntimeException e) {
      log.warn(
          "No se pudo deshacer lo preparado para la decisión {}: {}", d.getId(), e.getMessage());
    }
  }

  private Approval claim(long id, Long userId, String role, String status, String comment) {
    return tx.execute(
        s -> {
          Approval a =
              approvals
                  .findById(id)
                  .orElseThrow(() -> BusinessException.notFound("Aprobación", id));
          if (!"pending".equals(a.getStatus())) {
            throw BusinessException.conflict("already_resolved", "La aprobación ya fue resuelta");
          }
          if (!"expired".equals(status)
              && !DECIDERS.getOrDefault(a.getRequestedRole(), Set.of("admin")).contains(role)) {
            throw BusinessException.forbidden(
                "Esta aprobación le corresponde al rol " + a.getRequestedRole());
          }
          a.setStatus(status);
          a.setDecidedBy(userId);
          a.setDecidedAt(Instant.now(clock));
          a.setComment(comment == null ? null : DecisionRecorder.truncate(comment, 500));
          return a;
        });
  }

  private void resolved(Approval a, AiDecision d) {
    tx.executeWithoutResult(
        s ->
            events.publishEvent(
                new ApprovalResolved(
                    TenantContext.require(),
                    a.getId(),
                    d.getId(),
                    a.getStatus(),
                    d.getTitle(),
                    a.getDecidedBy())));
  }

  private static Map<String, Object> resultRef(AiDecision d) {
    return d.getResultRef() == null ? Map.of() : d.getResultRef();
  }
}
