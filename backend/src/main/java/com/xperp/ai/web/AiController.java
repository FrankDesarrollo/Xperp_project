package com.xperp.ai.web;

import com.xperp.ai.agent.FinancialAdvisor;
import com.xperp.ai.agent.StockAdvisor;
import com.xperp.ai.api.AiProperties;
import com.xperp.ai.decisions.AiDecision;
import com.xperp.ai.decisions.AiDecisionRef;
import com.xperp.ai.decisions.AiUsageReport;
import com.xperp.ai.decisions.AlertService;
import com.xperp.ai.decisions.ApprovalService;
import com.xperp.ai.decisions.DecisionService;
import com.xperp.ai.decisions.TriggerKind;
import com.xperp.ai.forecasting.ForecastService;
import com.xperp.ai.policies.AiSettings;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.policies.AiUsageService;
import com.xperp.ai.policies.PolicyAdminService;
import com.xperp.ai.policies.PolicyEngine;
import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.shared.contract.api.AiApi;
import com.xperp.shared.contract.model.AiDecisionPage;
import com.xperp.shared.contract.model.AiUsage;
import com.xperp.shared.contract.model.AiUsageDay;
import com.xperp.shared.contract.model.Alert;
import com.xperp.shared.contract.model.Approval;
import com.xperp.shared.contract.model.ApprovalBatchFailure;
import com.xperp.shared.contract.model.ApprovalBatchRequest;
import com.xperp.shared.contract.model.ApprovalBatchResult;
import com.xperp.shared.contract.model.ApprovalDecisionRequest;
import com.xperp.shared.contract.model.ApprovalPage;
import com.xperp.shared.contract.model.AutonomyPolicy;
import com.xperp.shared.contract.model.DecisionReference;
import com.xperp.shared.contract.model.FinancialFinding;
import com.xperp.shared.contract.model.FinancialReview;
import com.xperp.shared.contract.model.FinancialReviewPage;
import com.xperp.shared.contract.model.ForecastSeries;
import com.xperp.shared.contract.model.ImpactItem;
import com.xperp.shared.contract.model.ReasonRequest;
import com.xperp.shared.contract.model.ScoringWeights;
import com.xperp.shared.contract.model.SeriesPoint;
import com.xperp.shared.contract.model.StockRecommendation;
import com.xperp.shared.contract.model.TriggerCounts;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.AuthenticatedUser;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AiController implements AiApi {

  private final DecisionService decisions;
  private final ApprovalService approvals;
  private final AiSettingsService settings;
  private final AiUsageService usage;
  private final PolicyAdminService policies;
  private final AlertService alerts;
  private final ForecastService forecasts;
  private final StockAdvisor advisor;
  private final FinancialAdvisor financial;
  private final AiUsageReport usageReport;
  private final UserDirectory users;
  private final AiProperties properties;
  private final Clock clock;

  AiController(
      DecisionService decisions,
      ApprovalService approvals,
      AiSettingsService settings,
      AiUsageService usage,
      PolicyAdminService policies,
      AlertService alerts,
      ForecastService forecasts,
      StockAdvisor advisor,
      FinancialAdvisor financial,
      AiUsageReport usageReport,
      UserDirectory users,
      AiProperties properties,
      Clock clock) {
    this.decisions = decisions;
    this.approvals = approvals;
    this.settings = settings;
    this.usage = usage;
    this.policies = policies;
    this.alerts = alerts;
    this.forecasts = forecasts;
    this.advisor = advisor;
    this.financial = financial;
    this.usageReport = usageReport;
    this.users = users;
    this.properties = properties;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- decisiones

  @Override
  public ResponseEntity<AiDecisionPage> listDecisions(
      String decisionType,
      String status,
      String excludeType,
      String referenceType,
      Long referenceId,
      Integer page,
      Integer size) {
    PageResult<com.xperp.shared.contract.model.AiDecision> result =
        decisions
            .list(decisionType, status, excludeType, referenceType, referenceId, page, size)
            .map(this::toModel);
    return ResponseEntity.ok(
        new AiDecisionPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  public ResponseEntity<com.xperp.shared.contract.model.AiDecision> getDecision(Long id) {
    return ResponseEntity.ok(toModel(decisions.get(id)));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager')")
  public ResponseEntity<com.xperp.shared.contract.model.AiDecision> revertDecision(
      Long id, ReasonRequest reason) {
    return ResponseEntity.ok(
        toModel(decisions.revert(id, reason == null ? null : reason.getReason())));
  }

  // ---------------------------------------------------------------- aprobaciones

  @Override
  public ResponseEntity<ApprovalPage> listApprovals(
      String status, Boolean mine, String decisionType, Integer page, Integer size) {
    AuthenticatedUser user = CurrentUser.require();
    Set<String> roles =
        Boolean.TRUE.equals(mine) ? decidableRoles(user) : Set.of("purchasing", "manager", "admin");
    PageResult<Approval> result =
        approvals.list(status, roles, decisionType, page, size).map(this::toModel);
    return ResponseEntity.ok(
        new ApprovalPage(result.items(), result.total(), result.page(), result.size()));
  }

  private static Set<String> decidableRoles(AuthenticatedUser user) {
    return switch (user.role()) {
      case "admin" -> Set.of("purchasing", "manager", "admin");
      case "manager" -> Set.of("purchasing", "manager");
      case "purchasing" -> Set.of("purchasing");
      default -> Set.of("__none__");
    };
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','purchasing')")
  public ResponseEntity<Approval> approveApproval(Long id, ApprovalDecisionRequest body) {
    AuthenticatedUser user = CurrentUser.require();
    return ResponseEntity.ok(
        toModel(
            approvals.approve(
                id, user.id(), user.role(), body == null ? null : body.getComment())));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','purchasing')")
  public ResponseEntity<ApprovalBatchResult> batchApproveApprovals(ApprovalBatchRequest body) {
    AuthenticatedUser user = CurrentUser.require();
    ApprovalService.BatchResult r =
        approvals.approveAll(body.getIds(), user.id(), user.role(), body.getComment());
    return ResponseEntity.ok(
        new ApprovalBatchResult()
            .approved(r.approved())
            .failed(
                r.failed().entrySet().stream()
                    .map(
                        e ->
                            new ApprovalBatchFailure()
                                .id(e.getKey())
                                .message(e.getValue() == null ? "Falló" : e.getValue()))
                    .toList())
            .approvedAmount(Money.format(Money.round(r.amount()))));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','purchasing')")
  public ResponseEntity<Approval> rejectApproval(Long id, ApprovalDecisionRequest body) {
    AuthenticatedUser user = CurrentUser.require();
    return ResponseEntity.ok(
        toModel(
            approvals.reject(id, user.id(), user.role(), body == null ? null : body.getComment())));
  }

  // ---------------------------------------------------------------- uso y finanzas

  @Override
  public ResponseEntity<AiUsage> getAiUsage(LocalDate from, LocalDate to) {
    LocalDate today = SimTime.today(clock);
    LocalDate t = to == null ? today : to;
    LocalDate f = from == null ? t.withDayOfMonth(1) : from;
    if (f.isAfter(t)) {
      throw BusinessException.invalid("invalid_range", "La fecha inicial va antes de la final");
    }
    AiUsageReport.Report r = usageReport.report(f, t);
    return ResponseEntity.ok(
        new AiUsage()
            .from(r.from())
            .to(r.to())
            .decisionsTotal(r.decisions())
            .byTrigger(
                new TriggerCounts()
                    .event(r.byTrigger().get(TriggerKind.EVENT))
                    .condition(r.byTrigger().get(TriggerKind.CONDITION))
                    .request(r.byTrigger().get(TriggerKind.REQUEST))
                    .schedule(r.byTrigger().get(TriggerKind.SCHEDULE)))
            .decidedByRules(r.decidedByRules())
            .decidedWithLlm(r.decidedWithLlm())
            .llmCalls(r.llmCalls())
            .llmLimitPerDay(r.llmLimitPerDay())
            .tokensIn((int) Math.min(Integer.MAX_VALUE, r.tokensIn()))
            .tokensOut((int) Math.min(Integer.MAX_VALUE, r.tokensOut()))
            .estimatedCostUsd(
                r.estimatedCostUsd() == null ? null : r.estimatedCostUsd().toPlainString())
            .assistantQuestions(r.assistantQuestions())
            .autonomousFinancialActions(r.autonomousFinancialActions())
            .byDay(
                r.byDay().stream()
                    .map(
                        d ->
                            new AiUsageDay()
                                .date(d.date())
                                .decisions(d.decisions())
                                .llmCalls(d.llmCalls())
                                .tokens((int) Math.min(Integer.MAX_VALUE, d.tokens())))
                    .toList()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','purchasing','viewer')")
  public ResponseEntity<FinancialReviewPage> listFinancialReviews(Integer page, Integer size) {
    PageResult<FinancialReview> result =
        decisions
            .list(FinancialAdvisor.DECISION_TYPE, null, null, null, null, page, size)
            .map(AiController::toReview);
    return ResponseEntity.ok(
        new FinancialReviewPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','purchasing','viewer')")
  public ResponseEntity<FinancialReview> runFinancialReview() {
    long id = financial.review("user_request");
    return ResponseEntity.status(HttpStatus.CREATED).body(toReview(decisions.get(id)));
  }

  @SuppressWarnings("unchecked")
  static FinancialReview toReview(DecisionService.View v) {
    AiDecision d = v.decision();
    Map<String, Object> payload = d.getProposedAction() == null ? Map.of() : d.getProposedAction();
    Object rawFindings = payload.get("findings");
    List<Map<String, Object>> findings =
        rawFindings instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    LocalDate day = SimTime.dateOf(d.getSimTime());
    return new FinancialReview()
        .id(d.getId())
        .simTime(SimTime.toOffset(d.getSimTime()))
        .triggerEvent(d.getTriggerEvent())
        .triggerKind(TriggerKind.of(d.getTriggerEvent()))
        .periodFrom(date(payload.get("period_from"), day.withDayOfMonth(1)))
        .periodTo(date(payload.get("period_to"), day))
        .summary(String.valueOf(payload.getOrDefault("summary", d.getReasoning())))
        .findings(
            findings.stream()
                .map(
                    f ->
                        new FinancialFinding()
                            .code(String.valueOf(f.get("code")))
                            .severity(String.valueOf(f.get("severity")))
                            .title(String.valueOf(f.get("title")))
                            .detail(String.valueOf(f.get("detail")))
                            .recommendation(String.valueOf(f.get("recommendation")))
                            .impact(f.get("impact") == null ? null : f.get("impact").toString())
                            .link(f.get("link") == null ? null : f.get("link").toString()))
                .toList())
        .llmUsed(d.isLlmUsed());
  }

  private static LocalDate date(Object raw, LocalDate fallback) {
    return raw == null ? fallback : LocalDate.parse(raw.toString());
  }

  // ---------------------------------------------------------------- políticas y ajustes

  @Override
  public ResponseEntity<List<AutonomyPolicy>> listPolicies() {
    return ResponseEntity.ok(policies.list().stream().map(AiController::toModel).toList());
  }

  @Override
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<List<AutonomyPolicy>> updatePolicies(List<AutonomyPolicy> body) {
    policies.update(
        body.stream()
            .map(
                p ->
                    new PolicyAdminService.PolicyChange(
                        p.getId(),
                        p.getLevel(),
                        p.getMinAmount() == null || p.getMinAmount().isBlank()
                            ? null
                            : new BigDecimal(p.getMinAmount()),
                        p.getMaxAmount() == null || p.getMaxAmount().isBlank()
                            ? null
                            : new BigDecimal(p.getMaxAmount()),
                        p.getApproverRole() == null || p.getApproverRole().isBlank()
                            ? null
                            : p.getApproverRole(),
                        Boolean.TRUE.equals(p.getIsEnabled()),
                        p.getConditions()))
            .toList());
    return listPolicies();
  }

  @Override
  public ResponseEntity<com.xperp.shared.contract.model.AiSettings> getAiSettings() {
    return ResponseEntity.ok(toModel(settings.current()));
  }

  @Override
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<com.xperp.shared.contract.model.AiSettings> updateAiSettings(
      com.xperp.shared.contract.model.AiSettings body) {
    ScoringWeights w = body.getScoringWeights();
    double sum = w.getPrice() + w.getLeadTime() + w.getFulfillment() + w.getPreferred();
    if (Math.abs(sum - 1.0) > 0.01) {
      throw BusinessException.invalid("invalid_weights", "Los pesos del puntaje deben sumar 1");
    }
    AiSettings saved =
        settings.save(
            new AiSettings(
                body.getKillSwitch(),
                body.getShadowMode(),
                new BigDecimal(body.getAiDailySpendCap()),
                body.getLlmMaxCallsPerDay(),
                new SupplierScorer.Weights(
                    w.getPrice(), w.getLeadTime(), w.getFulfillment(), w.getPreferred())));
    return ResponseEntity.ok(toModel(saved));
  }

  // ---------------------------------------------------------------- alertas y pronóstico

  @Override
  public ResponseEntity<List<Alert>> listAlerts(Boolean unread, Integer limit) {
    return ResponseEntity.ok(
        alerts.recent(Boolean.TRUE.equals(unread), limit == null ? 50 : limit).stream()
            .map(
                a ->
                    new Alert(
                            a.getId(),
                            a.getType(),
                            a.getSeverity(),
                            a.getMessage(),
                            a.isRead(),
                            SimTime.toOffset(a.getRaisedAt()))
                        .entityType(a.getEntityType())
                        .entityId(a.getEntityId())
                        .aiDecisionId(a.getAiDecisionId()))
            .toList());
  }

  @Override
  public ResponseEntity<Void> markAlertRead(Long id) {
    alerts.markRead(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<ForecastSeries> getForecast(
      Long productId, Long warehouseId, Integer historyDays) {
    ForecastService.Series s =
        forecasts.series(
            productId, warehouseId, SimTime.today(clock), historyDays == null ? 45 : historyDays);
    return ResponseEntity.ok(
        new ForecastSeries(
            productId, warehouseId, s.model(), points(s.history()), points(s.forecast())));
  }

  @Override
  public ResponseEntity<List<StockRecommendation>> listStockRecommendations(
      Long warehouseId, Long productId) {
    return ResponseEntity.ok(
        advisor.recommendations(warehouseId, productId).stream()
            .map(AiController::toModel)
            .toList());
  }

  private static StockRecommendation toModel(StockAdvisor.Advice a) {
    return new StockRecommendation(
            a.row().stock().productId(),
            a.row().sku(),
            a.row().productName(),
            a.row().stock().warehouseId(),
            a.row().warehouseCode(),
            a.row().status(),
            a.kind().name().toLowerCase(Locale.ROOT),
            a.priority().name().toLowerCase(Locale.ROOT),
            a.row().stock().available(),
            a.row().inTransit(),
            a.row().stock().reorderPoint(),
            a.title(),
            a.reason())
        .quantity(a.quantity())
        .daysOfCover(a.daysOfCover())
        .supplierCode(a.supplierCode())
        .supplierName(a.supplierName())
        .leadTimeDays(a.leadTimeDays())
        .amount(a.amount() == null ? null : Money.format(a.amount()))
        .sourceWarehouseCode(a.sourceWarehouseCode())
        .expectedAt(a.expectedAt() == null ? null : SimTime.toOffset(a.expectedAt()));
  }

  private static List<SeriesPoint> points(Map<LocalDate, Double> values) {
    return values.entrySet().stream()
        .map(e -> new SeriesPoint(e.getKey(), Math.round(e.getValue() * 10) / 10.0))
        .toList();
  }

  // ---------------------------------------------------------------- mapeo

  private com.xperp.shared.contract.model.AiDecision toModel(DecisionService.View v) {
    AiDecision d = v.decision();
    List<DecisionReference> refs =
        dedupe(v.refs()).stream()
            .map(r -> new DecisionReference(r.getRefType(), r.getRefId(), r.getLabel()))
            .toList();
    return new com.xperp.shared.contract.model.AiDecision(
            d.getId(),
            d.getDecisionType(),
            d.getTriggerEvent(),
            TriggerKind.of(d.getTriggerEvent()),
            d.getInputSnapshot(),
            d.getAlternatives(),
            d.getProposedAction(),
            d.getReasoning(),
            d.getConfidence().doubleValue(),
            impact(d.getImpact()),
            d.getPolicyResult(),
            d.getStatus(),
            v.reversible(),
            d.isLlmUsed(),
            SimTime.toOffset(d.getSimTime()),
            SimTime.toOffset(d.getCreatedAt()),
            refs)
        .actionType(d.getActionType())
        .title(d.getTitle())
        .triggerRef(d.getTriggerRef())
        .amount(Money.format(d.getAmount()))
        .policyReason(d.getPolicyReason())
        .autonomyLevel(d.getAutonomyLevel())
        .resultMessage(d.getResultMessage())
        .approvedBy(d.getApprovedBy())
        .approvedByName(users.nameOf(d.getApprovedBy()))
        .approvalId(v.approvalId())
        .executedAt(SimTime.toOffset(d.getExecutedAt()))
        .revertedAt(SimTime.toOffset(d.getRevertedAt()))
        .llmModel(d.getLlmModel())
        .promptVersion(d.getPromptVersion())
        .tokensIn(d.getTokensIn())
        .tokensOut(d.getTokensOut())
        .latencyMs(d.getLatencyMs());
  }

  private static List<ImpactItem> impact(List<Map<String, Object>> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream()
        .map(
            m ->
                new ImpactItem()
                    .label(String.valueOf(m.get("label")))
                    .value(String.valueOf(m.get("value")))
                    .unit(String.valueOf(m.get("unit")))
                    .effect(String.valueOf(m.get("effect")))
                    .note(m.get("note") == null ? null : m.get("note").toString()))
        .toList();
  }

  private static List<AiDecisionRef> dedupe(List<AiDecisionRef> refs) {
    Map<String, AiDecisionRef> unique = new LinkedHashMap<>();
    for (AiDecisionRef r : refs) {
      unique.putIfAbsent(r.getRefType() + ":" + r.getRefId(), r);
    }
    return List.copyOf(unique.values());
  }

  private Approval toModel(com.xperp.ai.decisions.Approval a) {
    return new Approval(
            a.getId(),
            a.getAiDecisionId(),
            a.getRequestedRole(),
            a.getStatus(),
            SimTime.toOffset(a.getRequestedAt()),
            SimTime.toOffset(a.getExpiresAt()),
            toModel(decisions.get(a.getAiDecisionId())))
        .decidedBy(a.getDecidedBy())
        .decidedByName(users.nameOf(a.getDecidedBy()))
        .decidedAt(SimTime.toOffset(a.getDecidedAt()))
        .comment(a.getComment());
  }

  static AutonomyPolicy toModel(com.xperp.ai.policies.AutonomyPolicy p) {
    return new AutonomyPolicy(p.getId(), p.getActionType(), p.getLevel(), p.isEnabled())
        .description(p.getDescription())
        .minAmount(Money.format(p.getMinAmount()))
        .maxAmount(Money.format(p.getMaxAmount()))
        .approverRole(p.getApproverRole())
        .conditions(p.getConditions() == null ? Map.of() : p.getConditions())
        .requiresHuman(PolicyEngine.requiresHuman(p.getActionType()));
  }

  private com.xperp.shared.contract.model.AiSettings toModel(AiSettings s) {
    LocalDate today = SimTime.today(clock);
    return new com.xperp.shared.contract.model.AiSettings(
            s.killSwitch(),
            s.shadowMode(),
            Money.format(s.dailySpendCap()),
            s.llmMaxCallsPerDay(),
            new ScoringWeights(
                s.weights().price(),
                s.weights().leadTime(),
                s.weights().fulfillment(),
                s.weights().preferred()))
        .llmProvider(properties.llmProvider())
        .llmModel(properties.llmModel())
        .llmCallsToday(usage.llmCalls(today))
        .aiSpendToday(Money.format(usage.spend(today)));
  }
}
