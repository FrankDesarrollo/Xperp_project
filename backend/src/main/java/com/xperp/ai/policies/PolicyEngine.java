package com.xperp.ai.policies;

import com.xperp.ai.decisions.ApprovalRepository;
import com.xperp.ai.decisions.ApprovalService;
import com.xperp.catalog.api.CatalogService;
import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decide qué pasa con una propuesta (AGENTS.md, secciones 8.7 y 9): kill switch, modo sombra, nivel
 * de autonomía por tipo de acción y monto, presupuesto de la categoría y tope diario de compras de
 * la IA.
 */
@Component
public class PolicyEngine {

  public static final String EXECUTED = "executed";
  public static final String PENDING_APPROVAL = "pending_approval";
  public static final String SUGGESTED = "suggested";
  public static final String BLOCKED = "blocked";

  /** Acciones que gastan dinero y por eso pasan por presupuesto y tope diario. */
  static final List<String> SPENDING_ACTIONS =
      List.of("create_purchase_order", "reassign_purchase_order");

  /**
   * Acciones que mueven o comprometen dinero o activos. La IA las prepara y las explica, pero nunca
   * las ejecuta sola: siempre las aprueba una persona, sin importar el nivel configurado (ADR
   * 0004).
   */
  public static final Set<String> HUMAN_APPROVAL_ACTIONS =
      Set.of(
          "create_purchase_order",
          "reassign_purchase_order",
          "cancel_purchase_order",
          "propose_stock_transfer",
          "adjust_inventory",
          "apply_budget_plan",
          "create_shipment");

  /**
   * Atributo de una propuesta que nace de otra que una persona ya aprobó (las OC de un plan de
   * presupuesto aprobado). Lleva el id de esa decisión y se verifica contra su aprobación.
   */
  public static final String APPROVED_IN_DECISION = "approved_in_decision";

  public static boolean requiresHuman(String actionType) {
    return HUMAN_APPROVAL_ACTIONS.contains(actionType);
  }

  /**
   * Resultado de la política. {@code approvedIn} es la decisión aprobada por una persona de la que
   * esta hereda la aprobación, si la hay.
   */
  public record PolicyDecision(
      String result, Integer level, String approverRole, String reason, Long approvedIn) {

    public PolicyDecision(String result, Integer level, String approverRole, String reason) {
      this(result, level, approverRole, reason, null);
    }

    public boolean notifyUsers() {
      return EXECUTED.equals(result) && level != null && level == 2;
    }
  }

  private final AutonomyPolicyRepository policies;
  private final AiSettingsService settings;
  private final AiUsageService usage;
  private final FinanceService finance;
  private final CatalogService catalog;
  private final ApprovalRepository approvals;
  private final Clock clock;

  PolicyEngine(
      AutonomyPolicyRepository policies,
      AiSettingsService settings,
      AiUsageService usage,
      FinanceService finance,
      CatalogService catalog,
      ApprovalRepository approvals,
      Clock clock) {
    this.policies = policies;
    this.settings = settings;
    this.usage = usage;
    this.finance = finance;
    this.catalog = catalog;
    this.approvals = approvals;
    this.clock = clock;
  }

  public PolicyDecision evaluate(ActionProposal p) {
    AiSettings s = settings.current();
    if (s.killSwitch()) {
      return new PolicyDecision(
          BLOCKED, null, null, "Kill switch activo: la IA no ejecuta acciones");
    }
    if (s.shadowMode()) {
      return new PolicyDecision(SUGGESTED, null, null, "Modo sombra: la IA decide pero no ejecuta");
    }
    Optional<AutonomyPolicy> policy = match(p);
    int level = policy.map(AutonomyPolicy::getLevel).orElse(1);
    String role =
        policy.map(AutonomyPolicy::getApproverRole).filter(r -> !r.isBlank()).orElse("manager");
    String reason =
        policy.map(AutonomyPolicy::getDescription).orElse("Sin política: requiere aprobación");

    if (level == 0) {
      return new PolicyDecision(SUGGESTED, 0, null, "Nivel 0: solo sugerencia. " + reason);
    }
    // Una persona ya la aprobó dentro de otra decisión (las OC de un plan de presupuesto aprobado).
    Optional<Long> upstream = approvedUpstream(p, role);
    if (level == 1 && upstream.isEmpty()) {
      return new PolicyDecision(PENDING_APPROVAL, 1, role, reason);
    }
    if (p.forcedApproval() != null) {
      return new PolicyDecision(
          PENDING_APPROVAL, 1, p.forcedApproval().role(), p.forcedApproval().reason());
    }
    if (SPENDING_ACTIONS.contains(p.actionType()) && p.amount() != null) {
      Optional<String> budget = budgetProblem(p);
      if (budget.isPresent()) {
        return new PolicyDecision(PENDING_APPROVAL, 1, "manager", budget.get());
      }
      LocalDate today = SimTime.today(clock);
      BigDecimal spent = usage.spend(today);
      if (spent.add(p.amount()).compareTo(s.dailySpendCap()) > 0) {
        return new PolicyDecision(
            PENDING_APPROVAL,
            1,
            "manager",
            "Supera el tope diario de compras de la IA (%s; ya ejecutó %s)"
                .formatted(Money.display(s.dailySpendCap()), Money.display(spent)));
      }
    }
    if (upstream.isPresent()) {
      return new PolicyDecision(
          EXECUTED,
          level,
          null,
          "Aprobada por una persona en la decisión #%d. %s".formatted(upstream.get(), reason),
          upstream.get());
    }
    if (requiresHuman(p.actionType())) {
      return new PolicyDecision(
          PENDING_APPROVAL,
          1,
          role,
          "Mueve dinero o activos: la aprueba una persona antes de ejecutarse. " + reason);
    }
    return new PolicyDecision(EXECUTED, level, null, reason);
  }

  /**
   * La decisión de origen, si la propuesta dice venir de una, esa decisión está aprobada y quien la
   * aprobó podía decidir también el rol que pide esta.
   */
  private Optional<Long> approvedUpstream(ActionProposal p, String role) {
    Object raw = p.attributes().get(APPROVED_IN_DECISION);
    if (raw == null) {
      return Optional.empty();
    }
    long parentId = Long.parseLong(raw.toString());
    return approvals
        .findByAiDecisionId(parentId)
        .filter(a -> "approved".equals(a.getStatus()))
        .filter(a -> ApprovalService.roleCanDecide(a.getRequestedRole(), role))
        .map(a -> parentId);
  }

  /** La política más específica que cumple tipo de acción, rango de monto y condiciones. */
  Optional<AutonomyPolicy> match(ActionProposal p) {
    List<AutonomyPolicy> candidates = policies.findByActionTypeAndEnabledTrue(p.actionType());
    for (AutonomyPolicy policy : candidates) {
      if (!inRange(policy, p.amount())) {
        continue;
      }
      if (!conditionsMet(policy.getConditions(), p.attributes())) {
        continue;
      }
      return Optional.of(policy);
    }
    return Optional.empty();
  }

  static boolean inRange(AutonomyPolicy policy, BigDecimal amount) {
    if (amount == null) {
      return policy.getMinAmount() == null || policy.getMinAmount().signum() == 0;
    }
    if (policy.getMinAmount() != null && amount.compareTo(policy.getMinAmount()) < 0) {
      return false;
    }
    return policy.getMaxAmount() == null || amount.compareTo(policy.getMaxAmount()) < 0;
  }

  /** Condiciones simples sobre atributos numéricos: {@code {"overcost_pct_lte": 10}}. */
  static boolean conditionsMet(Map<String, Object> conditions, Map<String, Object> attributes) {
    if (conditions == null || conditions.isEmpty()) {
      return true;
    }
    for (Map.Entry<String, Object> c : conditions.entrySet()) {
      String key = c.getKey();
      double limit = Double.parseDouble(String.valueOf(c.getValue()));
      if (key.endsWith("_lte")) {
        Object v = attributes.get(key.substring(0, key.length() - 4));
        if (v == null || Double.parseDouble(String.valueOf(v)) > limit) {
          return false;
        }
      } else if (key.endsWith("_gt")) {
        Object v = attributes.get(key.substring(0, key.length() - 3));
        if (v == null || Double.parseDouble(String.valueOf(v)) <= limit) {
          return false;
        }
      }
    }
    return true;
  }

  /** {@code committed + spent + nueva_oc ≤ amount} en cada categoría de la OC (sección 6.7). */
  private Optional<String> budgetProblem(ActionProposal p) {
    Object raw = p.attributes().get("amounts_by_category");
    if (!(raw instanceof Map<?, ?> byCategory)) {
      return Optional.empty();
    }
    String period = SimTime.period(clock.instant());
    for (Map.Entry<?, ?> e : byCategory.entrySet()) {
      long categoryId = Long.parseLong(e.getKey().toString());
      BigDecimal amount = new BigDecimal(e.getValue().toString());
      BudgetDto budget = finance.budget(categoryId, period);
      if (budget.amount().signum() > 0
          && budget.used().add(amount).compareTo(budget.amount()) > 0) {
        return Optional.of(
            "Excede el presupuesto de %s: disponibles %s y la OC suma %s"
                .formatted(
                    catalog.category(categoryId).name(),
                    Money.display(budget.available()),
                    Money.display(amount)));
      }
    }
    return Optional.empty();
  }
}
