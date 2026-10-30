package com.xperp.ai.decisions;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Entidad que tocó una decisión (OC, pedido, traslado...). */
@Getter
@Setter
@Entity
@Table(name = "ai_decision_refs")
public class AiDecisionRef extends TenantEntity {

  @Column(name = "ai_decision_id", nullable = false)
  private Long aiDecisionId;

  @Column(name = "ref_type", nullable = false, length = 30)
  private String refType;

  @Column(name = "ref_id", nullable = false)
  private Long refId;

  @Column(nullable = false, length = 100)
  private String label;
}
