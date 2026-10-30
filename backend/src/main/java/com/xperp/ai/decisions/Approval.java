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
@Table(name = "approvals")
public class Approval extends TenantEntity {

  @Column(name = "ai_decision_id", nullable = false)
  private Long aiDecisionId;

  @Column(name = "requested_role", nullable = false, length = 20)
  private String requestedRole;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "decided_by")
  private Long decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(length = 500)
  private String comment;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
