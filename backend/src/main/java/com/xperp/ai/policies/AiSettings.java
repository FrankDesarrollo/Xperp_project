package com.xperp.ai.policies;

import com.xperp.ai.scoring.SupplierScorer;
import java.math.BigDecimal;

/** Ajustes vigentes de la IA (tabla system_settings). */
public record AiSettings(
    boolean killSwitch,
    boolean shadowMode,
    BigDecimal dailySpendCap,
    int llmMaxCallsPerDay,
    SupplierScorer.Weights weights) {

  /** active, shadow o stopped: lo que muestra la barra superior. */
  public String mode() {
    if (killSwitch) {
      return "stopped";
    }
    return shadowMode ? "shadow" : "active";
  }
}
