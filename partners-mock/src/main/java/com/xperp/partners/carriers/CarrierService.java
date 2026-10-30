package com.xperp.partners.carriers;

import com.xperp.partners.PartnersProperties;
import com.xperp.partners.clock.BackendClock;
import com.xperp.partners.webhook.EventScheduler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Transportadoras simuladas: cotizan según tarifa y cobertura, crean envíos y programan los cambios
 * de estado (en tránsito y entregado) por webhook.
 */
@Service
public class CarrierService {

  static final ZoneId ZONE = ZoneId.of("America/Bogota");

  public record Quote(String service, String cost, int etaDays) {}

  private final PartnersProperties properties;
  private final BackendClock clock;
  private final EventScheduler scheduler;
  private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

  CarrierService(PartnersProperties properties, BackendClock clock, EventScheduler scheduler) {
    this.properties = properties;
    this.clock = clock;
    this.scheduler = scheduler;
    clock.onReset(counters::clear);
  }

  public boolean exists(String code) {
    return properties.carriers().containsKey(code);
  }

  /** Cotización; vacío si no cubre la ciudad de destino. */
  public Optional<Quote> quote(String code, String origin, String destination, double weightKg) {
    PartnersProperties.Carrier c = properties.carriers().get(code);
    if (!c.covers(destination)) {
      return Optional.empty();
    }
    BigDecimal cost =
        c.baseRate()
            .add(c.ratePerKg().multiply(BigDecimal.valueOf(weightKg)))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    int min = c.etaDays().get(0);
    int max = c.etaDays().size() > 1 ? c.etaDays().get(1) : min;
    int eta = min + Math.floorMod((origin + destination).hashCode(), max - min + 1);
    String service = "TRP-03".equals(code) ? "Express" : "Estándar";
    return Optional.of(new Quote(service, cost.toPlainString(), eta));
  }

  public String createShipment(
      String code, String orderNumber, String origin, String destination, double weightKg) {
    PartnersProperties.Carrier c = properties.carriers().get(code);
    Instant now = Optional.ofNullable(clock.now()).orElse(Instant.EPOCH);
    String tracking =
        "%s-%07d"
            .formatted(
                code.replace("-", ""),
                counters.computeIfAbsent(code, k -> new AtomicInteger()).incrementAndGet());
    Random rng =
        com.xperp.partners.suppliers.Seeds.random(
            properties.randomSeed(), "carrier:" + orderNumber);
    int eta = quote(code, origin, destination, weightKg).map(Quote::etaDays).orElse(2);
    boolean late = rng.nextDouble() > c.onTimeRate();
    Instant inTransit = now.plus(Duration.ofMinutes(60 + rng.nextInt(120)));
    Instant delivered =
        now.plus(Duration.ofDays(eta + (late ? 1 : 0))).plus(Duration.ofMinutes(rng.nextInt(300)));
    schedule(tracking, inTransit, "in_transit");
    schedule(tracking, delivered, "delivered");
    return tracking;
  }

  private void schedule(String tracking, Instant at, String status) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tracking_number", tracking);
    body.put("status", status);
    body.put("occurred_at", at.atZone(ZONE).toOffsetDateTime().toString());
    scheduler.schedule(new EventScheduler.Scheduled(at, "carriers", tracking, status, body));
  }
}
