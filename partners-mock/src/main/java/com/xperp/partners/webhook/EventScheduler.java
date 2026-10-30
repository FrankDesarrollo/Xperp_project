package com.xperp.partners.webhook;

import com.xperp.partners.clock.BackendClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cola de webhooks programados en hora simulada. Cada segundo envía los que ya vencieron según el
 * reloj del backend; si el backend no responde, los reintenta.
 */
@Component
public class EventScheduler {

  /** Webhook programado. {@code tag} agrupa los de una misma OC o envío. */
  public record Scheduled(
      Instant at, String path, String tag, String kind, Map<String, Object> body) {}

  private final List<Scheduled> queue = new ArrayList<>();
  private final BackendClock clock;
  private final WebhookClient webhooks;

  EventScheduler(BackendClock clock, WebhookClient webhooks) {
    this.clock = clock;
    this.webhooks = webhooks;
    clock.onReset(this::clear);
  }

  public synchronized void schedule(Scheduled event) {
    queue.add(event);
  }

  /** Quita los eventos pendientes que cumplan la condición (por ejemplo, al reprogramar). */
  public synchronized void cancel(Predicate<Scheduled> filter) {
    queue.removeIf(filter);
  }

  public synchronized List<Scheduled> pending(Predicate<Scheduled> filter) {
    return queue.stream().filter(filter).toList();
  }

  public synchronized void clear() {
    queue.clear();
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 1000)
  void dispatch() {
    Instant now = clock.now();
    if (now == null) {
      return;
    }
    List<Scheduled> due;
    synchronized (this) {
      due =
          queue.stream()
              .filter(e -> !e.at().isAfter(now))
              .sorted(Comparator.comparing(Scheduled::at))
              .toList();
    }
    for (Scheduled e : due) {
      if (webhooks.send(e.path(), e.body())) {
        synchronized (this) {
          queue.remove(e);
        }
      } else {
        return;
      }
    }
  }
}
