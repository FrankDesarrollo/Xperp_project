package com.xperp.ai.assistant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

  List<AssistantMessage> findByConversationIdOrderByIdAsc(String conversationId);
}
