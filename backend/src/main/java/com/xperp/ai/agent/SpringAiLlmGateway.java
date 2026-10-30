package com.xperp.ai.agent;

import com.xperp.ai.api.AiProperties;
import com.xperp.ai.policies.ActionProposal.LlmTrace;
import com.xperp.ai.tools.ReadTools;
import com.xperp.shared.config.JsonSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LLM real con Spring AI. El proveedor (Anthropic, OpenAI u Ollama) lo decide LLM_PROVIDER sin
 * tocar este código. Las respuestas estructuradas usan {@code ChatClient.entity(...)}.
 */
@Component
public class SpringAiLlmGateway implements LlmGateway {

  private static final Logger log = LoggerFactory.getLogger(SpringAiLlmGateway.class);

  private static final String SYSTEM =
      """
      Eres el agente de operaciones de Xperp, el ERP de Distribuidora La Ceiba S.A.S. (empresa
      ficticia, datos demo). Respondes siempre en español, en máximo tres frases, y citas los
      números que usaste. No inventas datos: si necesitas más contexto, usa las herramientas de
      lectura. Nunca ejecutas acciones directamente.
      """;

  /** Salida estructurada de una elección. */
  public record ChoiceOutput(String option, String reasoning, Double confidence) {}

  private final ObjectProvider<ChatModel> chatModel;
  private final PromptLibrary prompts;
  private final ReadTools readTools;
  private final JsonSupport json;
  private final AiProperties properties;
  private volatile ChatClient client;

  SpringAiLlmGateway(
      ObjectProvider<ChatModel> chatModel,
      PromptLibrary prompts,
      ReadTools readTools,
      JsonSupport json,
      AiProperties properties) {
    this.chatModel = chatModel;
    this.prompts = prompts;
    this.readTools = readTools;
    this.json = json;
    this.properties = properties;
  }

  @Override
  public boolean available() {
    return !properties.mock() && chatModel.getIfAvailable() != null;
  }

  @Override
  public String model() {
    String model = properties.llmModel();
    return properties.llmProvider() + (model == null || model.isBlank() ? "" : ":" + model);
  }

  private ChatClient client() {
    ChatClient local = client;
    if (local == null) {
      local = ChatClient.builder(chatModel.getObject()).defaultSystem(SYSTEM).build();
      client = local;
    }
    return local;
  }

  @Override
  public Choice choose(
      String prompt,
      Map<String, Object> variables,
      List<Option> options,
      int fallbackIndex,
      String fallbackReasoning) {
    Map<String, Object> vars = new LinkedHashMap<>(variables);
    List<Map<String, Object>> opts = new ArrayList<>();
    for (Option o : options) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", o.id());
      m.put("label", o.label());
      m.putAll(o.data());
      opts.add(m);
    }
    vars.put("options", json.write(opts));
    String user =
        render(prompt, vars)
            + "\n\nResponde con el id de la opción elegida en 'option', tu justificación en"
            + " 'reasoning' y tu confianza entre 0 y 1 en 'confidence'.";
    long start = System.nanoTime();
    var response =
        client().prompt().user(user).tools(readTools).call().responseEntity(ChoiceOutput.class);
    int latency = (int) ((System.nanoTime() - start) / 1_000_000);
    ChoiceOutput out = response.entity();
    int index = fallbackIndex;
    if (out != null && out.option() != null) {
      for (int i = 0; i < options.size(); i++) {
        if (options.get(i).id().equalsIgnoreCase(out.option().trim())) {
          index = i;
        }
      }
    }
    String reasoning = out == null || out.reasoning() == null ? fallbackReasoning : out.reasoning();
    double confidence = out == null || out.confidence() == null ? 0.7 : out.confidence();
    return new Choice(index, reasoning, confidence, trace(prompt, response.response(), latency));
  }

  @Override
  public Text write(String prompt, Map<String, Object> variables, String fallback) {
    Map<String, Object> vars = new LinkedHashMap<>(variables);
    vars.putIfAbsent("facts", fallback);
    long start = System.nanoTime();
    ChatResponse response = client().prompt().user(render(prompt, vars)).call().chatResponse();
    int latency = (int) ((System.nanoTime() - start) / 1_000_000);
    String text =
        response == null || response.getResult() == null
            ? fallback
            : response.getResult().getOutput().getText();
    return new Text(
        text == null || text.isBlank() ? fallback : text.trim(), trace(prompt, response, latency));
  }

  @Override
  public Answer answer(List<Turn> history, String question) {
    List<Message> messages = new ArrayList<>();
    for (Turn t : history) {
      messages.add(
          "assistant".equals(t.role())
              ? new AssistantMessage(t.content())
              : new UserMessage(t.content()));
    }
    long start = System.nanoTime();
    ChatResponse response =
        client()
            .prompt()
            .system(prompts.get("assistant").template())
            .messages(messages)
            .user(question)
            .tools(readTools)
            .call()
            .chatResponse();
    int latency = (int) ((System.nanoTime() - start) / 1_000_000);
    String text =
        response == null || response.getResult() == null
            ? "No pude responder en este momento."
            : response.getResult().getOutput().getText();
    return new Answer(text, readTools.drainCalls(), trace("assistant", response, latency));
  }

  private String render(String prompt, Map<String, Object> vars) {
    PromptLibrary.Prompt p = prompts.get(prompt);
    Map<String, Object> safe = new LinkedHashMap<>();
    vars.forEach((k, v) -> safe.put(k, v == null ? "" : v));
    return new PromptTemplate(p.template()).render(safe);
  }

  private LlmTrace trace(String prompt, ChatResponse response, int latency) {
    Integer in = null;
    Integer out = null;
    if (response != null && response.getMetadata() != null) {
      Usage usage = response.getMetadata().getUsage();
      if (usage != null) {
        in = usage.getPromptTokens();
        out = usage.getCompletionTokens();
      }
    }
    log.debug("LLM {} en {} ms (tokens {}/{})", prompt, latency, in, out);
    return new LlmTrace(true, model(), prompts.version(prompt), in, out, latency);
  }
}
