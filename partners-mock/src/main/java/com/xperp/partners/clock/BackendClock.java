package com.xperp.partners.clock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xperp.partners.PartnersProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Hora simulada del backend (AGENTS.md, 10.1): se lee cada segundo de {@code GET
 * /api/v1/simulation/clock}. Si el reloj retrocede (reinicio de la demo), avisa a quien escuche
 * para que borre su estado.
 */
@Component
public class BackendClock {

  private static final Logger log = LoggerFactory.getLogger(BackendClock.class);

  record ClockResponse(@JsonProperty("sim_time") OffsetDateTime simTime, String status) {}

  private final RestClient http;
  private final AtomicReference<Instant> now = new AtomicReference<>();
  private final List<Runnable> resetListeners = new ArrayList<>();
  private volatile boolean warned;

  BackendClock(PartnersProperties properties, RestClient.Builder builder) {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.http = builder.baseUrl(properties.backendUrl()).requestFactory(factory).build();
  }

  /** Hora simulada actual; null mientras el backend no responda. */
  public Instant now() {
    return now.get();
  }

  public void onReset(Runnable listener) {
    resetListeners.add(listener);
  }

  @Scheduled(fixedRate = 1000)
  void poll() {
    try {
      ClockResponse r =
          http.get().uri("/api/v1/simulation/clock").retrieve().body(ClockResponse.class);
      if (r == null || r.simTime() == null) {
        return;
      }
      Instant next = r.simTime().toInstant();
      Instant previous = now.getAndSet(next);
      if (previous != null && next.isBefore(previous.minus(Duration.ofHours(2)))) {
        log.info("El reloj del backend retrocedió: se reinicia el estado de los socios");
        resetListeners.forEach(Runnable::run);
      }
      warned = false;
    } catch (RestClientException e) {
      if (!warned) {
        log.warn("El backend no responde todavía: {}", e.getMessage());
        warned = true;
      }
    }
  }
}
