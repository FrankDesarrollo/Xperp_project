package com.xperp.shared.sse;

import com.xperp.shared.security.AuthenticatedUser;
import com.xperp.shared.tenancy.TenantContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Conexiones SSE abiertas y envío de eventos (AGENTS.md, sección 7.6).
 *
 * <p>Los eventos se encolan y se envían cada 500 ms. Los que se repiten mucho (por ejemplo, {@code
 * inventory.changed}) se agrupan por llave: solo sale el último de cada llave en cada envío.
 */
@Component
public class SseHub {

  private static final Logger log = LoggerFactory.getLogger(SseHub.class);

  /** Destinatarios de un evento: todos, un rol o un usuario. */
  public record Audience(String role, Long userId) {
    public static final Audience ALL = new Audience(null, null);

    public static Audience role(String role) {
      return new Audience(role, null);
    }

    public static Audience user(long userId) {
      return new Audience(null, userId);
    }

    boolean matches(AuthenticatedUser user) {
      if (userId != null) {
        return userId == user.id();
      }
      return role == null || role.equals(user.role()) || user.isAdmin();
    }
  }

  private record Client(SseEmitter emitter, AuthenticatedUser user) {}

  private record Pending(String event, Object data, Audience audience) {}

  private final Map<Long, List<Client>> clients = new ConcurrentHashMap<>();
  private final Map<Long, Map<String, Pending>> queues = new ConcurrentHashMap<>();
  private long sequence;

  public SseEmitter register(AuthenticatedUser user) {
    SseEmitter emitter = new SseEmitter(0L);
    Client client = new Client(emitter, user);
    List<Client> list =
        clients.computeIfAbsent(user.companyId(), k -> new CopyOnWriteArrayList<>());
    list.add(client);
    Runnable remove = () -> list.remove(client);
    emitter.onCompletion(remove);
    emitter.onTimeout(remove);
    emitter.onError(e -> remove.run());
    try {
      emitter.send(SseEmitter.event().comment("conectado"));
    } catch (IOException e) {
      remove.run();
    }
    return emitter;
  }

  /** Encola un evento para todos los usuarios de la empresa actual. */
  public void publish(String event, Object data) {
    publish(event, data, Audience.ALL, null);
  }

  /**
   * Encola un evento. Si hay una transacción activa, se encola al hacer commit. {@code coalesceKey}
   * agrupa eventos repetidos; null para no agrupar.
   */
  public void publish(String event, Object data, Audience audience, String coalesceKey) {
    Long companyId = TenantContext.current();
    if (companyId == null || TenantContext.ROOT.equals(companyId)) {
      return;
    }
    Runnable enqueue = () -> enqueue(companyId, event, data, audience, coalesceKey);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              enqueue.run();
            }
          });
    } else {
      enqueue.run();
    }
  }

  private synchronized void enqueue(
      long companyId, String event, Object data, Audience audience, String coalesceKey) {
    if (!clients.containsKey(companyId) || clients.get(companyId).isEmpty()) {
      return;
    }
    String key = coalesceKey == null ? event + "#" + (sequence++) : event + ":" + coalesceKey;
    queues
        .computeIfAbsent(companyId, k -> new LinkedHashMap<>())
        .put(key, new Pending(event, data, audience));
  }

  @Scheduled(fixedDelay = 500)
  void flush() {
    Map<Long, Map<String, Pending>> batch;
    synchronized (this) {
      if (queues.isEmpty()) {
        return;
      }
      batch = new LinkedHashMap<>(queues);
      queues.clear();
    }
    batch.forEach(
        (companyId, events) -> {
          List<Client> targets = clients.getOrDefault(companyId, List.of());
          for (Pending p : events.values()) {
            for (Client c : targets) {
              if (p.audience().matches(c.user())) {
                send(companyId, c, p);
              }
            }
          }
        });
  }

  @Scheduled(fixedDelay = 15_000)
  void heartbeat() {
    clients.forEach(
        (companyId, list) -> {
          for (Client c : new ArrayList<>(list)) {
            try {
              c.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
              list.remove(c);
            }
          }
        });
  }

  private void send(long companyId, Client client, Pending p) {
    try {
      client
          .emitter()
          .send(SseEmitter.event().name(p.event()).data(p.data(), MediaType.APPLICATION_JSON));
    } catch (Exception e) {
      log.debug("Cliente SSE desconectado: {}", e.getMessage());
      clients.getOrDefault(companyId, List.of()).remove(client);
    }
  }

  public int connectedClients() {
    return clients.values().stream().mapToInt(List::size).sum();
  }
}
