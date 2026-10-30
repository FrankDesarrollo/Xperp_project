package com.xperp.notifications.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends TenantEntity {

  @Column(name = "user_id")
  private Long userId;

  @Column(length = 20)
  private String role;

  @Column(nullable = false, length = 40)
  private String type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(length = 300)
  private String link;

  @Column(name = "read_at")
  private Instant readAt;
}
