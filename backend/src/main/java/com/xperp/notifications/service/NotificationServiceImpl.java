package com.xperp.notifications.service;

import com.xperp.notifications.api.NotificationService;
import com.xperp.notifications.domain.Notification;
import com.xperp.notifications.repository.NotificationRepository;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.sse.SseHub;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository notifications;
  private final UserDirectory users;
  private final SseHub sse;
  private final Clock clock;

  NotificationServiceImpl(
      NotificationRepository notifications, UserDirectory users, SseHub sse, Clock clock) {
    this.notifications = notifications;
    this.users = users;
    this.sse = sse;
    this.clock = clock;
  }

  @Override
  public void notifyRole(
      String role, String type, String title, String body, String link, boolean alsoAdmins) {
    Set<Long> targets = new LinkedHashSet<>();
    users.byRole(role).forEach(u -> targets.add(u.id()));
    if (alsoAdmins) {
      users.byRole("admin").forEach(u -> targets.add(u.id()));
    }
    for (Long userId : targets) {
      save(userId, role, type, title, body, link);
    }
  }

  @Override
  public void notifyUser(long userId, String type, String title, String body, String link) {
    save(userId, null, type, title, body, link);
  }

  private void save(Long userId, String role, String type, String title, String body, String link) {
    Notification n = new Notification();
    n.setUserId(userId);
    n.setRole(role);
    n.setType(type);
    n.setTitle(title.length() > 200 ? title.substring(0, 197) + "..." : title);
    n.setBody(body == null ? "" : body);
    n.setLink(link);
    notifications.save(n);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", n.getId());
    data.put("type", type);
    data.put("title", n.getTitle());
    data.put("body", n.getBody());
    data.put("link", link);
    data.put("created_at", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
    sse.publish("notification.created", data, SseHub.Audience.user(userId), null);
  }

  @Transactional(readOnly = true)
  public PageResult<Notification> list(long userId, Integer page, Integer size) {
    return PageResult.of(
        notifications.findByUserId(
            userId, PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"))),
        n -> n);
  }

  @Transactional(readOnly = true)
  public long unread(long userId) {
    return notifications.countByUserIdAndReadAtIsNull(userId);
  }

  public void markRead(long id, long userId) {
    Notification n =
        notifications
            .findById(id)
            .orElseThrow(() -> BusinessException.notFound("Notificación", id));
    if (n.getUserId() == null || n.getUserId() != userId) {
      throw BusinessException.notFound("Notificación", id);
    }
    if (n.getReadAt() == null) {
      n.setReadAt(Instant.now(clock));
    }
  }

  public void markAllRead(long userId) {
    Instant now = Instant.now(clock);
    notifications.findByUserIdAndReadAtIsNull(userId).forEach(n -> n.setReadAt(now));
  }
}
