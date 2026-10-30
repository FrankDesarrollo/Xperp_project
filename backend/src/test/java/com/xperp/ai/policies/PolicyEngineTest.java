package com.xperp.ai.policies;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reglas de coincidencia de políticas: rangos de monto (sección 9.2) y condiciones. */
class PolicyEngineTest {

  private static AutonomyPolicy policy(String min, String max) {
    AutonomyPolicy p = new AutonomyPolicy();
    p.setMinAmount(min == null ? null : new BigDecimal(min));
    p.setMaxAmount(max == null ? null : new BigDecimal(max));
    return p;
  }

  @Test
  void amountRangesFollowSection92() {
    AutonomyPolicy small = policy(null, "5000000");
    AutonomyPolicy medium = policy("5000000", "20000000.01");
    AutonomyPolicy large = policy("20000000.01", null);

    BigDecimal four = new BigDecimal("4999999.99");
    BigDecimal five = new BigDecimal("5000000");
    BigDecimal twenty = new BigDecimal("20000000");
    BigDecimal more = new BigDecimal("20000001");

    assertThat(PolicyEngine.inRange(small, four)).isTrue();
    assertThat(PolicyEngine.inRange(small, five)).isFalse();
    assertThat(PolicyEngine.inRange(medium, five)).isTrue();
    assertThat(PolicyEngine.inRange(medium, twenty)).isTrue();
    assertThat(PolicyEngine.inRange(large, twenty)).isFalse();
    assertThat(PolicyEngine.inRange(large, more)).isTrue();
  }

  @Test
  void policiesWithoutRangeMatchActionsWithoutAmount() {
    assertThat(PolicyEngine.inRange(policy(null, null), null)).isTrue();
    assertThat(PolicyEngine.inRange(policy("5000000", null), null)).isFalse();
  }

  @Test
  void overcostConditionSplitsReassignments() {
    Map<String, Object> low = Map.of("overcost_pct_lte", 10);
    Map<String, Object> high = Map.of("overcost_pct_gt", 10);
    assertThat(PolicyEngine.conditionsMet(low, Map.of("overcost_pct", 5.3))).isTrue();
    assertThat(PolicyEngine.conditionsMet(high, Map.of("overcost_pct", 5.3))).isFalse();
    assertThat(PolicyEngine.conditionsMet(low, Map.of("overcost_pct", 15.8))).isFalse();
    assertThat(PolicyEngine.conditionsMet(high, Map.of("overcost_pct", 15.8))).isTrue();
    assertThat(PolicyEngine.conditionsMet(low, Map.of())).isFalse();
    assertThat(PolicyEngine.conditionsMet(Map.of(), Map.of())).isTrue();
  }

  @Test
  void notifiesOnlyLevelTwoExecutions() {
    assertThat(new PolicyEngine.PolicyDecision(PolicyEngine.EXECUTED, 2, null, "").notifyUsers())
        .isTrue();
    assertThat(new PolicyEngine.PolicyDecision(PolicyEngine.EXECUTED, 3, null, "").notifyUsers())
        .isFalse();
    assertThat(
            new PolicyEngine.PolicyDecision(PolicyEngine.PENDING_APPROVAL, 1, "manager", "")
                .notifyUsers())
        .isFalse();
  }

  @Test
  void outcomeSummaryTellsTheLlmWhatHappened() {
    assertThat(
            new ActionGateway.Outcome(
                    12, PolicyEngine.PENDING_APPROVAL, "pending_approval", "", Map.of())
                .summary())
        .contains("pendiente de aprobación");
    assertThat(
            new ActionGateway.Outcome(
                    3, PolicyEngine.BLOCKED, "blocked", "Kill switch activo", Map.of())
                .summary())
        .startsWith("bloqueada");
  }
}
