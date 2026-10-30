package com.xperp.ai.decisions;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro de una decisión de la IA (AGENTS.md, 6.8): qué vio, qué alternativas tenía, qué eligió y
 * por qué.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_decisions")
public class AiDecision extends TenantEntity {

  @Column(name = "decision_type", nullable = false, length = 40)
  private String decisionType;

  @Column(name = "action_type", length = 40)
  private String actionType;

  @Column(length = 200)
  private String title;

  @Column(name = "trigger_event", nullable = false, length = 60)
  private String triggerEvent;

  @Column(name = "trigger_ref", length = 100)
  private String triggerRef;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> inputSnapshot = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> alternatives = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "proposed_action", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> proposedAction = new LinkedHashMap<>();

  @Column(nullable = false, columnDefinition = "text")
  private String reasoning;

  @Column(nullable = false, precision = 4, scale = 3)
  private BigDecimal confidence;

  @Column(precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(name = "policy_result", nullable = false, length = 20)
  private String policyResult;

  @Column(name = "policy_reason", length = 300)
  private String policyReason;

  @Column(name = "autonomy_level")
  private Integer autonomyLevel;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "result_message", length = 500)
  private String resultMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_ref", columnDefinition = "jsonb")
  private Map<String, Object> resultRef;

  @Column(name = "approved_by")
  private Long approvedBy;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "reverted_at")
  private Instant revertedAt;

  @Column(name = "llm_used", nullable = false)
  private boolean llmUsed;

  @Column(name = "llm_model", length = 80)
  private String llmModel;

  @Column(name = "prompt_version", length = 40)
  private String promptVersion;

  @Column(name = "tokens_in")
  private Integer tokensIn;

  @Column(name = "tokens_out")
  private Integer tokensOut;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "sim_time", nullable = false)
  private Instant simTime;
}
