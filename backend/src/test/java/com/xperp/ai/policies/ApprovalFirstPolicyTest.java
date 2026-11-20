package com.xperp.ai.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.catalog.api.CatalogService;
import com.xperp.finance.api.FinanceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ADR 0004: la IA recomienda y una persona aprueba toda acción con impacto financiero o sobre el
 * inventario, aunque una política mal configurada diga lo contrario.
 */
class ApprovalFirstPolicyTest {

  private AutonomyPolicyRepository repo;
  private AiSettingsService settings;
  private AiUsageService usage;
  private PolicyEngine engine;

  @BeforeEach
  void setUp() {
    repo = mock(AutonomyPolicyRepository.class);
    settings = mock(AiSettingsService.class);
    usage = mock(AiUsageService.class);
    when(settings.current()).thenReturn(settings(false, false));
    when(usage.spend(any())).thenReturn(BigDecimal.ZERO);
    Clock clock = Clock.fixed(Instant.parse("2026-11-05T11:00:00Z"), ZoneId.of("America/Bogota"));
    engine =
        new PolicyEngine(
            repo, settings, usage, mock(FinanceService.class), mock(CatalogService.class), clock);
  }

  private static AiSettings settings(boolean kill, boolean shadow) {
    return new AiSettings(
        kill,
        shadow,
        new BigDecimal("40000000"),
        30,
        new SupplierScorer.Weights(0.40, 0.25, 0.25, 0.10));
  }

  private void policy(String action, int level, String role) {
    AutonomyPolicy p = new AutonomyPolicy();
    p.setActionType(action);
    p.setLevel(level);
    p.setApproverRole(role);
    p.setDescription("prueba");
    p.setConditions(new java.util.LinkedHashMap<>());
    when(repo.findByActionTypeAndEnabledTrue(anyString())).thenReturn(List.of());
    when(repo.findByActionTypeAndEnabledTrue(action)).thenReturn(List.of(p));
  }

  private static ActionProposal proposal(String action, String amount) {
    return ActionProposal.builder("test", action)
        .title("t")
        .amount(amount == null ? null : new BigDecimal(amount))
        .build();
  }

  @Test
  void smallPurchaseOrderNeedsApprovalEvenIfPolicySaysExecute() {
    policy("create_purchase_order", 2, null);
    PolicyEngine.PolicyDecision d = engine.evaluate(proposal("create_purchase_order", "1200000"));
    assertThat(d.result()).isEqualTo(PolicyEngine.PENDING_APPROVAL);
    assertThat(d.approverRole()).isEqualTo("purchasing");
    assertThat(d.reason()).contains("una persona aprueba");
  }

  @Test
  void everyProtectedActionWaitsForAPerson() {
    for (String action : PolicyEngine.APPROVAL_REQUIRED_ACTIONS) {
      policy(action, 3, null);
      assertThat(engine.evaluate(proposal(action, null)).result())
          .as(action)
          .isEqualTo(PolicyEngine.PENDING_APPROVAL);
    }
  }

  @Test
  void transfersAndAdjustmentsGoToManagement() {
    assertThat(PolicyEngine.defaultApprover("propose_stock_transfer")).isEqualTo("manager");
    assertThat(PolicyEngine.defaultApprover("adjust_inventory")).isEqualTo("manager");
    assertThat(PolicyEngine.defaultApprover("cancel_purchase_order")).isEqualTo("purchasing");
  }

  @Test
  void nonFinancialActionsStillRunOnTheirOwn() {
    for (String action :
        List.of("raise_alert", "create_warehouse_task", "create_shipment", "hold_sales_order")) {
      policy(action, 2, null);
      assertThat(engine.evaluate(proposal(action, null)).result())
          .as(action)
          .isEqualTo(PolicyEngine.EXECUTED);
    }
  }

  @Test
  void levelZeroStaysAsSuggestion() {
    policy("create_purchase_order", 0, null);
    assertThat(engine.evaluate(proposal("create_purchase_order", "100")).result())
        .isEqualTo(PolicyEngine.SUGGESTED);
  }

  @Test
  void killSwitchAndShadowModeStillWin() {
    policy("create_purchase_order", 1, "purchasing");
    when(settings.current()).thenReturn(settings(true, false));
    assertThat(engine.evaluate(proposal("create_purchase_order", "100")).result())
        .isEqualTo(PolicyEngine.BLOCKED);
    when(settings.current()).thenReturn(settings(false, true));
    assertThat(engine.evaluate(proposal("create_purchase_order", "100")).result())
        .isEqualTo(PolicyEngine.SUGGESTED);
  }

  @Test
  void impactRiskComparesCoverAgainstLeadTime() {
    assertThat(Impact.riskFromCover(1.5, 2)).isEqualTo(Impact.HIGH);
    assertThat(Impact.riskFromCover(4, 2)).isEqualTo(Impact.MEDIUM);
    assertThat(Impact.riskFromCover(12, 2)).isEqualTo(Impact.LOW);
    assertThat(Impact.riskFromCover(Double.POSITIVE_INFINITY, 2)).isEqualTo(Impact.LOW);
  }

  @Test
  void impactSurvivesTheRoundTripThroughJson() {
    Impact original =
        Impact.of(new BigDecimal("1250000.00"), "Compromete", "Repone", "Quiebre", Impact.HIGH);
    Map<String, Object> stored = original.toMap();
    assertThat(stored).containsEntry("amount", "1250000.00").containsEntry("risk_level", "high");
    Impact back = Impact.fromMap(stored);
    assertThat(back.amount()).isEqualByComparingTo("1250000");
    assertThat(back.riskIfRejected()).isEqualTo("Quiebre");
    assertThat(Impact.fromMap(Map.of())).isNull();
  }

  @Test
  void usagePercentageAfterTheOrder() {
    assertThat(PolicyEngine.usagePct(new BigDecimal("59500000"), new BigDecimal("70000000")))
        .isEqualTo(85.0);
    assertThat(PolicyEngine.usagePct(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
  }
}
