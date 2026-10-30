package com.xperp.notifications.web;

import com.xperp.notifications.domain.Notification;
import com.xperp.notifications.service.NotificationServiceImpl;
import com.xperp.shared.contract.api.NotificationsApi;
import com.xperp.shared.contract.model.NotificationPage;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.AuthenticatedUser;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.time.SimTime;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Notificaciones y flujo SSE ({@code GET /api/v1/stream}). El flujo es un controller escrito a
 * mano: el generador no produce una firma útil para {@code text/event-stream}.
 */
@RestController
class NotificationsController implements NotificationsApi {

  private final NotificationServiceImpl notifications;
  private final SseHub sse;

  NotificationsController(NotificationServiceImpl notifications, SseHub sse) {
    this.notifications = notifications;
    this.sse = sse;
  }

  @Override
  public ResponseEntity<NotificationPage> listNotifications(Integer page, Integer size) {
    AuthenticatedUser user = CurrentUser.require();
    PageResult<com.xperp.shared.contract.model.Notification> result =
        notifications.list(user.id(), page, size).map(NotificationsController::toModel);
    return ResponseEntity.ok(
        new NotificationPage(
            result.items(),
            result.total(),
            result.page(),
            result.size(),
            notifications.unread(user.id())));
  }

  @Override
  public ResponseEntity<Void> markNotificationRead(Long id) {
    notifications.markRead(id, CurrentUser.require().id());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> markAllNotificationsRead() {
    notifications.markAllRead(CurrentUser.require().id());
    return ResponseEntity.noContent().build();
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter stream() {
    return sse.register(CurrentUser.require());
  }

  static com.xperp.shared.contract.model.Notification toModel(Notification n) {
    return new com.xperp.shared.contract.model.Notification(
            n.getId(), n.getType(), n.getTitle(), n.getBody(), SimTime.toOffset(n.getCreatedAt()))
        .link(n.getLink())
        .readAt(SimTime.toOffset(n.getReadAt()));
  }
}
