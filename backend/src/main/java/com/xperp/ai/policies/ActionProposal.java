package com.xperp.ai.policies;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Propuesta de acción que llega al {@link ActionGateway}, venga de las reglas o del LLM (AGENTS.md,
 * sección 8.7). Lleva todo lo necesario para la bitácora: datos de entrada, alternativas,
 * razonamiento y, si aplica, la traza del LLM.
 */
public record ActionProposal(
    String decisionType,
    String actionType,
    String title,
    String triggerEvent,
    String triggerRef,
    Map<String, Object> inputSnapshot,
    List<Map<String, Object>> alternatives,
    Map<String, Object> payload,
    String reasoning,
    double confidence,
    BigDecimal amount,
    Map<String, Object> attributes,
    List<Ref> references,
    LlmTrace llm,
    ForcedApproval forcedApproval) {

  /** Entidad relacionada, para filtrar la bitácora y enlazar desde la interfaz. */
  public record Ref(String type, long id, String label) {}

  /** Traza del LLM; {@link #NONE} si se decidió por reglas. */
  public record LlmTrace(
      boolean used,
      String model,
      String promptVersion,
      Integer tokensIn,
      Integer tokensOut,
      Integer latencyMs) {
    public static final LlmTrace NONE = new LlmTrace(false, null, null, null, null, null);
  }

  /** Obliga a pedir aprobación aunque la política permita ejecutar. */
  public record ForcedApproval(String role, String reason) {}

  public static Builder builder(String decisionType, String actionType) {
    return new Builder(decisionType, actionType);
  }

  public static final class Builder {
    private final String decisionType;
    private final String actionType;
    private String title;
    private String triggerEvent = "manual";
    private String triggerRef;
    private Map<String, Object> input = new LinkedHashMap<>();
    private List<Map<String, Object>> alternatives = new ArrayList<>();
    private Map<String, Object> payload = new LinkedHashMap<>();
    private String reasoning = "";
    private double confidence = 1.0;
    private BigDecimal amount;
    private Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<Ref> refs = new ArrayList<>();
    private LlmTrace llm = LlmTrace.NONE;
    private ForcedApproval forced;

    private Builder(String decisionType, String actionType) {
      this.decisionType = decisionType;
      this.actionType = actionType;
    }

    public Builder title(String v) {
      title = v;
      return this;
    }

    public Builder trigger(String event, String ref) {
      triggerEvent = event;
      triggerRef = ref;
      return this;
    }

    public Builder input(Map<String, Object> v) {
      input = v;
      return this;
    }

    public Builder alternatives(List<Map<String, Object>> v) {
      alternatives = v;
      return this;
    }

    public Builder payload(Map<String, Object> v) {
      payload = v;
      return this;
    }

    public Builder reasoning(String v) {
      reasoning = v;
      return this;
    }

    public Builder confidence(double v) {
      confidence = v;
      return this;
    }

    public Builder amount(BigDecimal v) {
      amount = v;
      return this;
    }

    public Builder attribute(String key, Object value) {
      attributes.put(key, value);
      return this;
    }

    public Builder ref(String type, long id, String label) {
      refs.add(new Ref(type, id, label));
      return this;
    }

    public Builder llm(LlmTrace v) {
      llm = v == null ? LlmTrace.NONE : v;
      return this;
    }

    public Builder forceApproval(String role, String reason) {
      forced = new ForcedApproval(role, reason);
      return this;
    }

    public ActionProposal build() {
      return new ActionProposal(
          decisionType,
          actionType,
          title,
          triggerEvent,
          triggerRef,
          input,
          alternatives,
          payload,
          reasoning,
          confidence,
          amount,
          attributes,
          List.copyOf(refs),
          llm,
          forced);
    }
  }
}
