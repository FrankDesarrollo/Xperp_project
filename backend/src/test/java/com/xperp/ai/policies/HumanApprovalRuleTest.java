package com.xperp.ai.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xperp.ai.decisions.Approval;
import com.xperp.ai.decisions.ApprovalRepository;
import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.catalog.api.CatalogService;
import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.shared.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regla de ADR 0004: lo que mueve dinero o activos nunca se ejecuta sin una persona, aunque alguien
 * configure el nivel 2 o 3. La lista va escrita aquí a propósito: si alguien saca una acción de
 * {@link PolicyEngine#HUMAN_APPROVAL_ACTIONS}, esta prueba cae.
 */
class HumanApprovalRuleTest {

  /** Acciones que comprometen dinero o activos (ADR 0004). */
  static final List<String> FINANCIAL =
      List.of(
          "create_purchase_order",
          "reassign_purchase_order",
          "cancel_purchase_order",
          "propose_stock_transfer",
          "adjust_inventory",
          "apply_budget_plan",
          "create_shipment");

  private AutonomyPolicyRepository policies;
  private AiSettingsService settings;
  private ApprovalRepository approvals;
  private PolicyEngine engine;

  @BeforeEach
  void setUp() {
    policies = mock(AutonomyPolicyRepository.class);
    settings = mock(AiSettingsService.class);
    approvals = mock(ApprovalRepository.class);
    AiUsageService usage = mock(AiUsageService.class);
    FinanceService finance = mock(FinanceService.class);
    when(settings.current()).thenReturn(settings(false, false));
    when(usage.spend(org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO);
    when(finance.budget(anyLong(), anyString()))
        .thenReturn(
            new BudgetDto(
                1, 1, "2026-11", new BigDecimal("1000000000"), BigDecimal.ZERO, BigDecimal.ZERO));
    engine =
        new PolicyEngine(
            policies,
            settings,
            usage,
            finance,
            mock(CatalogService.class),
            approvals,
            Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC));
  }

  private static AiSettings settings(boolean killSwitch, boolean shadow) {
    return new AiSettings(
        killSwitch, shadow, new BigDecimal("40000000"), 30, SupplierScorer.Weights.DEFAULT);
  }

  /** Política configurada con el nivel dado, como si un admin la hubiera subido. */
  private void configure(String action, int level, String role) {
    AutonomyPolicy p = new AutonomyPolicy();
    p.setActionType(action);
    p.setDescription("configurada en nivel " + level);
    p.setLevel(level);
    p.setApproverRole(role);
    p.setEnabled(true);
    when(policies.findByActionTypeAndEnabledTrue(action)).thenReturn(List.of(p));
  }

  private static ActionProposal proposal(String action) {
    return ActionProposal.builder("test", action)
        .title(action)
        .amount(new BigDecimal("1000"))
        .build();
  }

  @Test
  void financialActionsWaitForAPersonEvenAtLevelThree() {
    for (String action : FINANCIAL) {
      configure(action, 3, null);
      PolicyEngine.PolicyDecision d = engine.evaluate(proposal(action));
      assertThat(d.result()).as(action).isEqualTo(PolicyEngine.PENDING_APPROVAL);
      assertThat(d.approverRole()).as(action).isNotBlank();
      assertThat(d.reason()).as(action).contains("la aprueba una persona");
    }
  }

  @Test
  void theCodeListMatchesThisTest() {
    assertThat(PolicyEngine.HUMAN_APPROVAL_ACTIONS).containsExactlyInAnyOrderElementsOf(FINANCIAL);
  }

  @Test
  void operationalActionsStillRunAlone() {
    for (String action :
        List.of(
            "create_warehouse_task", "raise_alert", "update_reorder_params", "hold_sales_order")) {
      configure(action, 3, null);
      assertThat(engine.evaluate(proposal(action)).result())
          .as(action)
          .isEqualTo(PolicyEngine.EXECUTED);
    }
  }

  @Test
  void levelZeroStaysAsSuggestion() {
    configure("create_purchase_order", 0, null);
    assertThat(engine.evaluate(proposal("create_purchase_order")).result())
        .isEqualTo(PolicyEngine.SUGGESTED);
  }

  @Test
  void killSwitchAndShadowModeComeFirst() {
    configure("create_purchase_order", 1, "purchasing");
    when(settings.current()).thenReturn(settings(true, false));
    assertThat(engine.evaluate(proposal("create_purchase_order")).result())
        .isEqualTo(PolicyEngine.BLOCKED);
    when(settings.current()).thenReturn(settings(false, true));
    assertThat(engine.evaluate(proposal("create_purchase_order")).result())
        .isEqualTo(PolicyEngine.SUGGESTED);
  }

  private void upstream(long decisionId, String requestedRole, String status) {
    Approval a = new Approval();
    a.setAiDecisionId(decisionId);
    a.setRequestedRole(requestedRole);
    a.setStatus(status);
    when(approvals.findByAiDecisionId(decisionId)).thenReturn(Optional.of(a));
  }

  private static ActionProposal child(long parentId) {
    return ActionProposal.builder("replenishment", "create_purchase_order")
        .title("OC del plan")
        .amount(new BigDecimal("1000"))
        .attribute(PolicyEngine.APPROVED_IN_DECISION, parentId)
        .build();
  }

  @Test
  void anOcInsideAnApprovedPlanDoesNotAskTwice() {
    configure("create_purchase_order", 1, "purchasing");
    upstream(77, "manager", "approved");
    PolicyEngine.PolicyDecision d = engine.evaluate(child(77));
    assertThat(d.result()).isEqualTo(PolicyEngine.EXECUTED);
    assertThat(d.approvedIn()).isEqualTo(77L);
  }

  @Test
  void theInheritedApprovalMustExistBeApprovedAndCoverTheRole() {
    configure("create_purchase_order", 1, "manager");
    // Sin aprobación de origen
    assertThat(engine.evaluate(child(80)).result()).isEqualTo(PolicyEngine.PENDING_APPROVAL);
    // Origen todavía pendiente o rechazado
    upstream(81, "manager", "pending");
    assertThat(engine.evaluate(child(81)).result()).isEqualTo(PolicyEngine.PENDING_APPROVAL);
    upstream(82, "manager", "rejected");
    assertThat(engine.evaluate(child(82)).result()).isEqualTo(PolicyEngine.PENDING_APPROVAL);
    // Lo aprobó Compras, pero esta OC le corresponde a Gerencia
    upstream(83, "purchasing", "approved");
    assertThat(engine.evaluate(child(83)).result()).isEqualTo(PolicyEngine.PENDING_APPROVAL);
  }

  @Test
  void policiesScreenCannotLiftAFinancialActionAboveLevelOne() {
    AutonomyPolicy p = new AutonomyPolicy();
    p.setActionType("create_shipment");
    p.setLevel(1);
    p.setApproverRole("manager");
    when(policies.findById(5L)).thenReturn(Optional.of(p));
    PolicyAdminService admin = new PolicyAdminService(policies);

    assertThatThrownBy(
            () ->
                admin.update(
                    List.of(
                        new PolicyAdminService.PolicyChange(
                            5L, 2, null, null, null, true, Map.of()))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("siempre la aprueba una persona");
    assertThat(p.getLevel()).isEqualTo(1);
  }
}
