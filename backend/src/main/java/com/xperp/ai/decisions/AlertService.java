package com.xperp.ai.decisions;

import com.xperp.ai.events.AlertRaised;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alertas de la IA (tabla alerts): precios, discrepancias, anomalías, presupuesto. */
@Service
@Transactional
public class AlertService {

  private final AlertRepository alerts;
  private final ApplicationEventPublisher events;
  private final SseHub sse;
  private final Clock clock;

  AlertService(AlertRepository alerts, ApplicationEventPublisher events, SseHub sse, Clock clock) {
    this.alerts = alerts;
    this.events = events;
    this.sse = sse;
    this.clock = clock;
  }

  public Alert raise(
      String type,
      String severity,
      String message,
      String entityType,
      Long entityId,
      Long decisionId) {
    Alert alert = new Alert();
    alert.setType(type);
    alert.setSeverity(severity);
    alert.setMessage(DecisionRecorder.truncate(message, 500));
    alert.setEntityType(entityType);
    alert.setEntityId(entityId);
    alert.setAiDecisionId(decisionId);
    alert.setRaisedAt(Instant.now(clock));
    alerts.save(alert);
    events.publishEvent(
        new AlertRaised(
            TenantContext.require(), alert.getId(), type, severity, alert.getMessage()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", alert.getId());
    data.put("type", type);
    data.put("severity", severity);
    data.put("message", alert.getMessage());
    sse.publish("alert.created", data);
    return alert;
  }

  @Transactional(readOnly = true)
  public List<Alert> recent(boolean unreadOnly, int limit) {
    PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
    return unreadOnly
        ? alerts.findByReadFalseOrderByIdDesc(page)
        : alerts.findAllByOrderByIdDesc(page);
  }

  public void markRead(long id) {
    Alert alert = alerts.findById(id).orElseThrow(() -> BusinessException.notFound("Alerta", id));
    alert.setRead(true);
  }
}
