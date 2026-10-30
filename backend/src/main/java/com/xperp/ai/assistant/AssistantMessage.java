package com.xperp.ai.assistant;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "assistant_messages")
public class AssistantMessage extends TenantEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "conversation_id", nullable = false, length = 40)
  private String conversationId;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_calls", columnDefinition = "jsonb")
  private List<Map<String, Object>> toolCalls;
}
