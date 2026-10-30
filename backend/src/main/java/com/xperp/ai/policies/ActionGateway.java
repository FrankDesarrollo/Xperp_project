package com.xperp.ai.policies;

import com.xperp.ai.decisions.ApprovalService;
import com.xperp.ai.decisions.DecisionRecorder;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.time.SimTime;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Única puerta de las acciones de la IA (AGENTS.md, secciones 8.6 y 8.7). La IA propone, el backend
 * valida y la política decide:
 *
 * <ol>
 *   <li>Validación de negocio del ejecutor.
 *   <li>{@link PolicyEngine}: kill switch, modo sombra, nivel, monto, presupuesto y tope diario.
 *   <li>Se ejecuta, se pide aprobación o queda como sugerencia.
 *   <li>Siempre se escribe en {@code ai_decisions}, también si quedó bloqueada.
 * </ol>
 */
@Component
public class ActionGateway {

  private static final Logger log = LoggerFactory.getLogger(ActionGateway.class);

  /** Resultado que se le devuelve a quien propuso (reglas, agente o tool del LLM). */
  public record Outcome(
      long decisionId,
      String policyResult,
      String status,
      String message,
      Map<String, Object> resultRef) {

    /** Texto corto para el LLM: "ejecutada OC-2026-000031", "pendiente de aprobación #12"... */
    public String summary() {
      return switch (policyResult) {
        case PolicyEngine.EXECUTED ->
            "failed".equals(status) ? "falló: " + message : "ejecutada: " + message;
        case PolicyEngine.PENDING_APPROVAL ->
            "pendiente de aprobación (decisión #" + decisionId + ")";
        case PolicyEngine.SUGGESTED -> "quedó como sugerencia: " + message;
        default -> "bloqueada: " + message;
      };
    }
  }

  private final PolicyEngine policies;
  private final ExecutorRegistry executors;
  private final DecisionRecorder recorder;
  private final ApprovalService approvals;
  private final AiUsageService usage;
  private final Clock clock;

  ActionGateway(
      PolicyEngine policies,
      ExecutorRegistry executors,
      DecisionRecorder recorder,
      ApprovalService approvals,
      AiUsageService usage,
      Clock clock) {
    this.policies = policies;
    this.executors = executors;
    this.recorder = recorder;
    this.approvals = approvals;
    this.usage = usage;
    this.clock = clock;
  }

  public Outcome submit(ActionProposal p) {
    ActionExecutor executor = executors.get(p.actionType());

    // 1. Validación de negocio
    try {
      executor.validate(p.payload());
    } catch (BusinessException | IllegalArgumentException e) {
      PolicyEngine.PolicyDecision invalid =
          new PolicyEngine.PolicyDecision(
              PolicyEngine.BLOCKED, null, null, "Validación: " + e.getMessage());
      long id = recorder.create(p, invalid, "blocked");
      recorder.finish(id, "blocked", e.getMessage(), null, List.of(), false);
      return new Outcome(id, PolicyEngine.BLOCKED, "blocked", e.getMessage(), Map.of());
    }

    // 2. Política
    PolicyEngine.PolicyDecision policy = policies.evaluate(p);
    String initial =
        switch (policy.result()) {
          case PolicyEngine.EXECUTED -> DecisionRecorder.STATUS_EXECUTING;
          case PolicyEngine.PENDING_APPROVAL -> "pending_approval";
          case PolicyEngine.SUGGESTED -> "suggested";
          default -> "blocked";
        };
    long id = recorder.create(p, policy, initial);

    // 3. Resultado
    switch (policy.result()) {
      case PolicyEngine.EXECUTED -> {
        try {
          ActionExecutor.Result result = executor.execute(id, p.payload());
          if (p.amount() != null && PolicyEngine.SPENDING_ACTIONS.contains(p.actionType())) {
            usage.addSpend(SimTime.today(clock), p.amount());
          }
          recorder.finish(
              id,
              "executed",
              result.message(),
              result.resultRef(),
              result.refs(),
              policy.notifyUsers());
          return new Outcome(id, policy.result(), "executed", result.message(), result.resultRef());
        } catch (RuntimeException e) {
          log.warn("Falló la acción {} (decisión {}): {}", p.actionType(), id, e.getMessage());
          recorder.finish(id, "failed", e.getMessage(), null, List.of(), true);
          return new Outcome(id, policy.result(), "failed", e.getMessage(), Map.of());
        }
      }
      case PolicyEngine.PENDING_APPROVAL -> {
        try {
          ActionExecutor.Result prepared = executor.prepare(id, p.payload());
          long approvalId = approvals.request(id, policy.approverRole(), p.title(), p.amount());
          String message =
              "Esperando aprobación de "
                  + roleName(policy.approverRole())
                  + " (#"
                  + approvalId
                  + ")";
          recorder.finish(
              id, "pending_approval", message, prepared.resultRef(), prepared.refs(), false);
          return new Outcome(
              id, policy.result(), "pending_approval", message, prepared.resultRef());
        } catch (RuntimeException e) {
          log.warn("No se pudo preparar {} (decisión {}): {}", p.actionType(), id, e.getMessage());
          recorder.finish(id, "failed", e.getMessage(), null, List.of(), true);
          return new Outcome(id, policy.result(), "failed", e.getMessage(), Map.of());
        }
      }
      default -> {
        recorder.finish(id, initial, policy.reason(), null, List.of(), false);
        return new Outcome(id, policy.result(), initial, policy.reason(), Map.of());
      }
    }
  }

  static String roleName(String role) {
    return switch (role == null ? "" : role) {
      case "purchasing" -> "Compras";
      case "manager" -> "Gerencia";
      case "admin" -> "Administración";
      default -> role;
    };
  }
}
