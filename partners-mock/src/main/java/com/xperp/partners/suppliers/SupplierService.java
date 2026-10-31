package com.xperp.partners.suppliers;

import com.xperp.partners.PartnersProperties;
import com.xperp.partners.clock.BackendClock;
import com.xperp.partners.scenarios.ScenarioStore;
import com.xperp.partners.webhook.EventScheduler;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Proveedores simulados: reciben la OC y programan su respuesta (confirmación, rechazo, retraso,
 * entrega parcial y entrega) según las probabilidades de partners.yml y el guion.
 */
@Service
public class SupplierService {

  private static final Logger log = LoggerFactory.getLogger(SupplierService.class);
  static final ZoneId ZONE = ZoneId.of("America/Bogota");

  public record Item(String sku, int quantity, String unitCost) {}

  public static final class Order {
    final String externalId;
    final String supplier;
    final String poNumber;
    final String warehouse;
    final List<Item> items;
    final Instant receivedAt;
    volatile String status = "received";
    volatile Instant expectedAt;
    volatile String reason;
    volatile Map<String, Integer> shipped;

    Order(
        String externalId,
        String supplier,
        String poNumber,
        String warehouse,
        List<Item> items,
        Instant receivedAt) {
      this.externalId = externalId;
      this.supplier = supplier;
      this.poNumber = poNumber;
      this.warehouse = warehouse;
      this.items = items;
      this.receivedAt = receivedAt;
    }
  }

  private final PartnersProperties properties;
  private final BackendClock clock;
  private final EventScheduler scheduler;
  private final ScenarioStore scenarios;
  private final Map<String, Order> orders = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

  SupplierService(
      PartnersProperties properties,
      BackendClock clock,
      EventScheduler scheduler,
      ScenarioStore scenarios) {
    this.properties = properties;
    this.clock = clock;
    this.scheduler = scheduler;
    this.scenarios = scenarios;
    clock.onReset(this::clear);
  }

  public void clear() {
    orders.clear();
    counters.clear();
  }

  public boolean exists(String code) {
    return properties.suppliers().containsKey(code);
  }

  public Order receive(String code, String poNumber, String warehouse, List<Item> items) {
    PartnersProperties.Supplier behavior = properties.suppliers().get(code);
    Instant now = Optional.ofNullable(clock.now()).orElse(Instant.EPOCH);
    String externalId =
        "%s-%05d"
            .formatted(
                code.replace("-", ""),
                counters.computeIfAbsent(code, k -> new AtomicInteger()).incrementAndGet());
    Order order = new Order(externalId, code, poNumber, warehouse, List.copyOf(items), now);
    orders.put(externalId, order);
    Random rng = Seeds.random(properties.randomSeed(), "supplier:" + poNumber);

    Optional<ScenarioStore.Reject> forced =
        scenarios.takeReject(code, items.stream().map(Item::sku).toList(), now);
    if (forced.isPresent() || rng.nextDouble() < behavior.rejectProbability()) {
      order.status = "rejected";
      order.reason = forced.map(ScenarioStore.Reject::reason).orElse("Sin existencias suficientes");
      schedule(order, now.plus(Duration.ofMinutes(120 + rng.nextInt(120))), "rejected");
      log.info("{} rechazará {}", code, poNumber);
      return order;
    }

    Instant confirmAt = now.plus(Duration.ofMinutes(60 + rng.nextInt(120)));
    int leadTime = behavior.leadTimeTo(warehouse);
    order.expectedAt = now.plus(Duration.ofDays(leadTime));
    schedule(order, confirmAt, "confirmed");

    if (rng.nextDouble() < behavior.delayProbability()) {
      Instant delayAt = confirmAt.plus(Duration.ofHours(Math.max(2, leadTime * 12L)));
      int extra = between(behavior.delayDays(), rng);
      // El guion manda: si ya tiene programado un retraso de este proveedor antes de la entrega,
      // no se suma otro al azar, que reasignaría la OC antes y dejaría al guion sin orden.
      if (!scenarios.delayPendingBefore(code, order.expectedAt)) {
        order.expectedAt = order.expectedAt.plus(Duration.ofDays(extra));
        order.reason = "Demoras en el despacho";
        schedule(order, delayAt, "delayed");
      }
    }
    if (rng.nextDouble() < behavior.partialProbability()) {
      Map<String, Integer> shipped = new LinkedHashMap<>();
      for (Item i : items) {
        shipped.put(
            i.sku(), Math.max(1, (int) Math.floor(i.quantity() * (0.6 + rng.nextDouble() * 0.3))));
      }
      order.shipped = shipped;
    }
    scheduleDelivery(order, confirmAt);
    return order;
  }

  private void scheduleDelivery(Order order, Instant notBefore) {
    Instant shipAt = order.expectedAt.minus(Duration.ofHours(12));
    if (shipAt.isBefore(notBefore.plus(Duration.ofMinutes(30)))) {
      shipAt = notBefore.plus(Duration.ofMinutes(30));
    }
    Instant deliverAt =
        order.expectedAt.isAfter(shipAt) ? order.expectedAt : shipAt.plus(Duration.ofHours(2));
    schedule(order, shipAt, "shipped");
    schedule(order, deliverAt, "delivered");
  }

  /** Aplica los retrasos forzados del guion a las órdenes abiertas del proveedor. */
  @Scheduled(fixedDelay = 1000)
  void applyScenarioDelays() {
    Instant now = clock.now();
    if (now == null) {
      return;
    }
    for (ScenarioStore.Delay d : scenarios.dueDelays(now)) {
      int affected = 0;
      for (Order o : orders.values()) {
        if (!o.supplier.equals(d.supplier())
            || "rejected".equals(o.status)
            || o.expectedAt == null) {
          continue;
        }
        boolean pendingDelivery =
            !scheduler
                .pending(e -> e.tag().equals(o.externalId) && "delivered".equals(e.kind()))
                .isEmpty();
        if (!pendingDelivery) {
          continue;
        }
        scheduler.cancel(
            e ->
                e.tag().equals(o.externalId)
                    && Set.of("shipped", "delivered", "delayed").contains(e.kind()));
        o.expectedAt = o.expectedAt.plus(Duration.ofDays(d.delayDays()));
        o.reason = d.reason();
        schedule(o, now, "delayed");
        scheduleDelivery(o, now);
        affected++;
      }
      log.info("Retraso forzado de {}: {} órdenes afectadas", d.supplier(), affected);
    }
  }

  private void schedule(Order order, Instant at, String status) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("external_id", order.externalId);
    body.put("po_number", order.poNumber);
    body.put("status", status);
    body.put(
        "expected_at",
        order.expectedAt == null
            ? null
            : order.expectedAt.atZone(ZONE).toOffsetDateTime().toString());
    if ("rejected".equals(status) || "delayed".equals(status)) {
      body.put("reason", order.reason);
    }
    if ("shipped".equals(status) || "delivered".equals(status)) {
      List<Map<String, Object>> items = new ArrayList<>();
      for (Item i : order.items) {
        int qty =
            order.shipped == null
                ? i.quantity()
                : order.shipped.getOrDefault(i.sku(), i.quantity());
        items.add(Map.of("sku", i.sku(), "quantity", qty));
      }
      body.put("items", items);
    }
    scheduler.schedule(
        new EventScheduler.Scheduled(at, "suppliers", order.externalId, status, body));
  }

  public Optional<Order> order(String externalId) {
    return Optional.ofNullable(orders.get(externalId));
  }

  /** Precios vigentes publicados: solo los que cambiaron (el alza del día 10 aparece aquí). */
  public List<Map<String, Object>> catalog(String code) {
    Instant now = clock.now();
    List<Map<String, Object>> result = new ArrayList<>();
    for (ScenarioStore.PriceChange p : scenarios.prices(code, now)) {
      result.add(
          Map.of(
              "sku", p.sku(),
              "unit_cost", p.unitCost().setScale(2).toPlainString(),
              "valid_from", p.at().atZone(ZONE).toOffsetDateTime().toString()));
    }
    return result;
  }

  static int between(List<Integer> range, Random rng) {
    int min = range.get(0);
    int max = range.size() > 1 ? range.get(1) : min;
    return min + rng.nextInt(max - min + 1);
  }
}
