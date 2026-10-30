package com.xperp.ai.agent;

import com.xperp.ai.policies.ActionProposal.LlmTrace;
import java.util.List;
import java.util.Map;

/**
 * Acceso al LLM (AGENTS.md, secciones 8.5 y 8.8). Hay dos implementaciones: {@link MockLlmGateway}
 * (elige la opción de mayor puntaje y redacta con plantilla) y {@link SpringAiLlmGateway}
 * (Anthropic, OpenAI u Ollama vía Spring AI). {@link LlmRouter} elige cuál usar y aplica el límite
 * diario de llamadas.
 */
public interface LlmGateway {

  /** Alternativa que el LLM puede elegir. {@code data} va en el prompt como JSON. */
  record Option(String id, String label, Map<String, Object> data) {}

  /** Elección del LLM con su justificación. */
  record Choice(int index, String reasoning, double confidence, LlmTrace trace) {}

  record Text(String text, LlmTrace trace) {}

  /** Turno de una conversación del asistente. */
  record Turn(String role, String content) {}

  record Answer(String text, List<Map<String, Object>> toolCalls, LlmTrace trace) {}

  /**
   * Pide al LLM elegir entre opciones. {@code fallbackIndex} y {@code fallbackReasoning} son la
   * decisión por reglas, que se usa en modo mock, si se pasa el límite o si el LLM falla.
   */
  Choice choose(
      String prompt,
      Map<String, Object> variables,
      List<Option> options,
      int fallbackIndex,
      String fallbackReasoning);

  /** Pide un texto (justificación o resumen). {@code fallback} es la plantilla con los números. */
  Text write(String prompt, Map<String, Object> variables, String fallback);

  /** Responde una pregunta del asistente con tools de solo lectura. */
  Answer answer(List<Turn> history, String question);

  boolean available();

  String model();
}
