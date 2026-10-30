package com.xperp.ai.policies;

import com.xperp.shared.error.BusinessException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edición de las políticas de autonomía desde IA → Políticas. */
@Service
@Transactional
public class PolicyAdminService {

  public record PolicyChange(
      long id,
      int level,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String approverRole,
      boolean enabled,
      Map<String, Object> conditions) {}

  private static final Set<String> ROLES = Set.of("purchasing", "manager", "admin");

  private final AutonomyPolicyRepository policies;

  PolicyAdminService(AutonomyPolicyRepository policies) {
    this.policies = policies;
  }

  @Transactional(readOnly = true)
  public List<AutonomyPolicy> list() {
    return policies.findAllByOrderByActionTypeAscMinAmountAscIdAsc();
  }

  public void update(List<PolicyChange> changes) {
    for (PolicyChange c : changes) {
      AutonomyPolicy p =
          policies
              .findById(c.id())
              .orElseThrow(() -> BusinessException.notFound("Política", c.id()));
      if (c.level() < 0 || c.level() > 3) {
        throw BusinessException.invalid("invalid_level", "El nivel va de 0 a 3");
      }
      if (c.level() == 1 && (c.approverRole() == null || !ROLES.contains(c.approverRole()))) {
        throw BusinessException.invalid(
            "invalid_role", "El nivel 1 necesita un rol aprobador válido");
      }
      p.setLevel(c.level());
      p.setMinAmount(c.minAmount());
      p.setMaxAmount(c.maxAmount());
      p.setApproverRole(c.approverRole());
      p.setEnabled(c.enabled());
      if (c.conditions() != null) {
        p.setConditions(new LinkedHashMap<>(c.conditions()));
      }
    }
  }
}
