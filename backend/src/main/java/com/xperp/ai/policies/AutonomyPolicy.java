package com.xperp.ai.policies;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Nivel de autonomía de un tipo de acción, por rango de monto y condiciones (AGENTS.md, 9). */
@Getter
@Setter
@Entity
@Table(name = "autonomy_policies")
public class AutonomyPolicy extends TenantEntity {

  @Column(name = "action_type", nullable = false, length = 40)
  private String actionType;

  @Column(length = 200)
  private String description;

  @Column(nullable = false)
  private int level;

  @Column(name = "min_amount", precision = 14, scale = 2)
  private BigDecimal minAmount;

  @Column(name = "max_amount", precision = 14, scale = 2)
  private BigDecimal maxAmount;

  @Column(name = "approver_role", length = 20)
  private String approverRole;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> conditions = new LinkedHashMap<>();

  @Column(name = "is_enabled", nullable = false)
  private boolean enabled = true;
}
