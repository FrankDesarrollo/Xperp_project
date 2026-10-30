package com.xperp.ai.agent;

import com.xperp.ai.policies.ActionProposal.LlmTrace;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.policies.AiUsageService;
import com.xperp.shared.time.SimTime;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Elige entre el LLM real y el mock, y aplica el límite de llamadas por día simulado (AGENTS.md,
 * 8.5). Si se pasa el límite o el LLM falla, decide por reglas y lo deja marcado en la traza.
 */
@Primary
@Component
public class LlmRouter implements LlmGateway {

  private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);
  static final String LIMIT_NOTE = " (Límite diario de LLM alcanzado: se decidió por reglas.)";

  private final SpringAiLlmGateway real;
  private final MockLlmGateway mock;
  private final AiUsageService usage;
  private final AiSettingsService settings;
  private final Clock clock;

  LlmRouter(
      SpringAiLlmGateway real,
      MockLlmGateway mock,
      AiUsageService usage,
      AiSettingsService settings,
      Clock clock) {
    this.real = real;
    this.mock = mock;
    this.usage = usage;
    this.settings = settings;
    this.clock = clock;
  }

  private enum Route {
    REAL,
    MOCK,
    LIMIT
  }

  private Route route() {
    if (!real.available()) {
      return Route.MOCK;
    }
    boolean allowed =
        usage.tryConsumeLlmCall(SimTime.today(clock), settings.current().llmMaxCallsPerDay());
    return allowed ? Route.REAL : Route.LIMIT;
  }

  @Override
  public Choice choose(
      String prompt,
      Map<String, Object> variables,
      List<Option> options,
      int fallbackIndex,
      String fallbackReasoning) {
    Route route = route();
    if (route == Route.REAL) {
      try {
        return real.choose(prompt, variables, options, fallbackIndex, fallbackReasoning);
      } catch (RuntimeException e) {
        log.warn("El LLM falló ({}); se decide por reglas", e.getMessage());
        return new Choice(
            fallbackIndex,
            fallbackReasoning + " (El LLM no respondió: se decidió por reglas.)",
            0.8,
            LlmTrace.NONE);
      }
    }
    if (route == Route.LIMIT) {
      return new Choice(fallbackIndex, fallbackReasoning + LIMIT_NOTE, 0.8, LlmTrace.NONE);
    }
    return mock.choose(prompt, variables, options, fallbackIndex, fallbackReasoning);
  }

  @Override
  public Text write(String prompt, Map<String, Object> variables, String fallback) {
    Route route = route();
    if (route == Route.REAL) {
      try {
        return real.write(prompt, variables, fallback);
      } catch (RuntimeException e) {
        log.warn("El LLM falló ({}); se usa la plantilla", e.getMessage());
        return new Text(fallback, LlmTrace.NONE);
      }
    }
    if (route == Route.LIMIT) {
      return new Text(fallback + LIMIT_NOTE, LlmTrace.NONE);
    }
    return mock.write(prompt, variables, fallback);
  }

  @Override
  public Answer answer(List<Turn> history, String question) {
    Route route = route();
    if (route == Route.REAL) {
      try {
        return real.answer(history, question);
      } catch (RuntimeException e) {
        log.warn("El asistente con LLM falló ({}); se responde con reglas", e.getMessage());
      }
    }
    return mock.answer(history, question);
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public String model() {
    return real.available() ? real.model() : mock.model();
  }
}
