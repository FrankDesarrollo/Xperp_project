package com.xperp.ai.agent;

import com.xperp.ai.assistant.MockAssistant;
import com.xperp.ai.policies.ActionProposal.LlmTrace;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * LLM simulado (LLM_PROVIDER=mock): elige siempre la opción de mayor puntaje y redacta con
 * plantilla. La demo funciona completa en este modo (AGENTS.md, 8.5).
 */
@Component
public class MockLlmGateway implements LlmGateway {

  public static final String MODEL = "mock";

  private final PromptLibrary prompts;
  private final MockAssistant assistant;

  MockLlmGateway(PromptLibrary prompts, MockAssistant assistant) {
    this.prompts = prompts;
    this.assistant = assistant;
  }

  @Override
  public Choice choose(
      String prompt,
      Map<String, Object> variables,
      List<Option> options,
      int fallbackIndex,
      String fallbackReasoning) {
    return new Choice(fallbackIndex, fallbackReasoning, 0.9, trace(prompt));
  }

  @Override
  public Text write(String prompt, Map<String, Object> variables, String fallback) {
    return new Text(fallback, trace(prompt));
  }

  @Override
  public Answer answer(List<Turn> history, String question) {
    MockAssistant.Reply reply = assistant.reply(question);
    return new Answer(reply.text(), reply.toolCalls(), trace("assistant"));
  }

  LlmTrace trace(String prompt) {
    return new LlmTrace(true, MODEL, prompts.version(prompt), null, null, 0);
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public String model() {
    return MODEL;
  }
}
