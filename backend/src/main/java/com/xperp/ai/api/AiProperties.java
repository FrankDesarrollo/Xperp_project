package com.xperp.ai.api;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** LLM_PROVIDER, LLM_MODEL, LLM_MAX_CALLS_PER_SIM_DAY y AI_SHADOW_MODE (valores iniciales). */
@ConfigurationProperties("xperp.ai")
public record AiProperties(
    @DefaultValue("mock") String llmProvider,
    String llmModel,
    @DefaultValue("30") int maxLlmCallsPerSimDay,
    @DefaultValue("false") boolean shadowMode,
    @DefaultValue("40000000") BigDecimal dailySpendCap) {

  public boolean mock() {
    return llmProvider == null || llmProvider.isBlank() || "mock".equalsIgnoreCase(llmProvider);
  }
}
