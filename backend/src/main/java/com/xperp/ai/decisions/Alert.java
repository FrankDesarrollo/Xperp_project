package com.xperp.ai.decisions;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "alerts")
public class Alert extends TenantEntity {

  @Column(nullable = false, length = 40)
  private String type;

  @Column(nullable = false, length = 10)
  private String severity;

  @Column(nullable = false, length = 500)
  private String message;

  @Column(name = "entity_type", length = 30)
  private String entityType;

  @Column(name = "entity_id")
  private Long entityId;

  @Column(name = "ai_decision_id")
  private Long aiDecisionId;

  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "raised_at", nullable = false)
  private Instant raisedAt;
}
