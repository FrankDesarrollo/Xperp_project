package com.xperp.ai.policies;

import com.xperp.catalog.api.CatalogService;
import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
   * Acciones con impacto financiero o sobre el inventario (ADR 0004): la IA las recomienda y una
   * persona las aprueba siempre. Ninguna política puede subirlas a nivel 2 o 3. Quedan fuera las
   * guías de transporte (flete rutinario cotizado) y la retención de pedidos anómalos (es la medida
   * de protección; una persona la libera o la revierte).
   */
  public static final Set<String> APPROVAL_REQUIRED_ACTIONS =
      Set.of(
          "create_purchase_order",
          "reassign_purchase_order",
          "cancel_purchase_order",
          "propose_stock_transfer",
          "adjust_inventory",
          "apply_budget_plan");

  /** Quién aprueba una acción protegida cuando la política no lo dice. */
  static String defaultApprover(String actionType) {
    return switch (actionType) {
      case "create_purchase_order", "reassign_purchase_order", "cancel_purchase_order" ->
          "purchasing";
      default -> "manager";
    };
  }

  public record PolicyDecision(String result, Integer level, String approverRole, String reason) {

    public boolean notifyUsers() {
      return EXECUTED.equals(result) && level != null && level == 2;
    }
  }

  private final AutonomyPolicyRepository policies;
  private final AiSettingsService settings;
  private final AiUsageService usage;
  private final FinanceService finance;
  private final CatalogService catalog;
  private final Clock clock;

  PolicyEngine(
      AutonomyPolicyRepository policies,
      AiSettingsService settings,
      AiUsageService usage,
      FinanceService finance,
      CatalogService catalog,
      Clock clock) {
    this.policies = policies;
    this.settings = settings;
    this.usage = usage;
    this.finance = finance;
    this.catalog = catalog;
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
        policy
            .map(AutonomyPolicy::getApproverRole)
            .filter(r -> r != null && !r.isBlank())
            .orElse(defaultApprover(p.actionType()));
    String reason =
        policy.map(AutonomyPolicy::getDescription).orElse("Sin política: requiere aprobación");

    if (level == 0) {
      return new PolicyDecision(SUGGESTED, 0, null, "Nivel 0: solo sugerencia. " + reason);
    }
    if (level == 1) {
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
    if (APPROVAL_REQUIRED_ACTIONS.contains(p.actionType())) {
      return new PolicyDecision(
          PENDING_APPROVAL,
          1,
          role,
          "Impacto financiero u operativo: la IA recomienda y una persona aprueba. " + reason);
    }
    return new PolicyDecision(EXECUTED, level, null, reason);
  }

  /**
   * Efecto de la propuesta en el presupuesto de cada categoría: disponible antes y después, y el
   * uso resultante. Lista vacía si la acción no gasta.
   */
  public List<Map<String, Object>> budgetImpact(ActionProposal p) {
    Object raw = p.attributes().get("amounts_by_category");
    if (!(raw instanceof Map<?, ?> byCategory)) {
      return List.of();
    }
    String period = SimTime.period(clock.instant());
    List<Map<String, Object>> lines = new ArrayList<>();
    for (Map.Entry<?, ?> e : byCategory.entrySet()) {
      long categoryId = Long.parseLong(e.getKey().toString());
      BigDecimal amount = new BigDecimal(e.getValue().toString());
      BudgetDto budget = finance.budget(categoryId, period);
      BigDecimal usedAfter = budget.used().add(amount);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("category", catalog.category(categoryId).name());
      m.put("period", period);
      m.put("budget", Money.format(budget.amount()));
      m.put("amount", Money.format(amount));
      m.put("available_before", Money.format(budget.available()));
      m.put("available_after", Money.format(budget.amount().subtract(usedAfter)));
      m.put("usage_pct_after", usagePct(usedAfter, budget.amount()));
      lines.add(m);
    }
    return lines;
  }

  static Double usagePct(BigDecimal used, BigDecimal total) {
    if (total == null || total.signum() == 0) {
      return null;
    }
    return used.multiply(BigDecimal.valueOf(100))
        .divide(total, 1, RoundingMode.HALF_UP)
        .doubleValue();
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
