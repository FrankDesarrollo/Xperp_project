package com.xperp.ai.assistant;

import com.xperp.ai.agent.LlmGateway;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat del tablero (AGENTS.md, 8.9). Solo usa tools de lectura y nunca genera SQL. Con
 * LLM_PROVIDER=mock responde con reglas y plantillas.
 */
@Service
public class AssistantService {

  public record Exchange(String conversationId, AssistantMessage reply) {}

  private final AssistantMessageRepository messages;
  private final LlmGateway llm;

  AssistantService(AssistantMessageRepository messages, LlmGateway llm) {
    this.messages = messages;
    this.llm = llm;
  }

  public Exchange ask(AuthenticatedUser user, String conversationId, String question) {
    String id =
        conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId;
    List<LlmGateway.Turn> history = new ArrayList<>();
    for (AssistantMessage m : messages.findByConversationIdOrderByIdAsc(id)) {
      if (!m.getUserId().equals(user.id())) {
        throw BusinessException.forbidden("La conversación es de otro usuario");
      }
      history.add(new LlmGateway.Turn(m.getRole(), m.getContent()));
    }
    save(user.id(), id, "user", question, null);
    LlmGateway.Answer answer = llm.answer(history, question);
    AssistantMessage reply = save(user.id(), id, "assistant", answer.text(), answer.toolCalls());
    return new Exchange(id, reply);
  }

  @Transactional(readOnly = true)
  public List<AssistantMessage> conversation(AuthenticatedUser user, String conversationId) {
    List<AssistantMessage> list = messages.findByConversationIdOrderByIdAsc(conversationId);
    if (list.isEmpty()) {
      throw BusinessException.notFound("Conversación", conversationId);
    }
    if (!list.get(0).getUserId().equals(user.id())) {
      throw BusinessException.forbidden("La conversación es de otro usuario");
    }
    return list;
  }

  @Transactional
  AssistantMessage save(
      long userId,
      String conversationId,
      String role,
      String content,
      List<java.util.Map<String, Object>> tools) {
    AssistantMessage m = new AssistantMessage();
    m.setUserId(userId);
    m.setConversationId(conversationId);
    m.setRole(role);
    m.setContent(content == null ? "" : content);
    m.setToolCalls(tools == null || tools.isEmpty() ? null : tools);
    return messages.save(m);
  }
}
