package com.xperp.ai.web;

import com.xperp.ai.assistant.AssistantMessage;
import com.xperp.ai.assistant.AssistantService;
import com.xperp.shared.contract.api.AssistantApi;
import com.xperp.shared.contract.model.AssistantConversation;
import com.xperp.shared.contract.model.AssistantMessageRequest;
import com.xperp.shared.contract.model.AssistantReply;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.time.SimTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AssistantController implements AssistantApi {

  private final AssistantService assistant;

  AssistantController(AssistantService assistant) {
    this.assistant = assistant;
  }

  @Override
  public ResponseEntity<AssistantReply> sendAssistantMessage(AssistantMessageRequest request) {
    AssistantService.Exchange exchange =
        assistant.ask(CurrentUser.require(), request.getConversationId(), request.getContent());
    return ResponseEntity.ok(
        new AssistantReply(exchange.conversationId(), toModel(exchange.reply())));
  }

  @Override
  public ResponseEntity<AssistantConversation> getAssistantConversation(String id) {
    return ResponseEntity.ok(
        new AssistantConversation(
            id,
            assistant.conversation(CurrentUser.require(), id).stream()
                .map(AssistantController::toModel)
                .toList()));
  }

  static com.xperp.shared.contract.model.AssistantMessage toModel(AssistantMessage m) {
    com.xperp.shared.contract.model.AssistantMessage model =
        new com.xperp.shared.contract.model.AssistantMessage(
            m.getId(), m.getRole(), m.getContent(), SimTime.toOffset(m.getCreatedAt()));
    if (m.getToolCalls() != null) {
      model.setToolCalls(m.getToolCalls());
    }
    return model;
  }
}
