package com.xperp.partners.scenarios;

import com.xperp.partners.clock.BackendClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Eventos forzados del guion (AGENTS.md, 10.3): los envía el backend al iniciar la simulación.
 * Retraso de PRV-06 el día 5, rechazo del arroz de PRV-02 desde el día 6 y alza del café de PRV-01
 * el día 10.
 */
@Component
public class ScenarioStore {

  public record Delay(String supplier, Instant at, int delayDays, String reason) {}

  public record Reject(String supplier, String sku, Instant from, String reason) {}

  public record PriceChange(String supplier, String sku, Instant at, BigDecimal unitCost) {}

  private final List<Delay> delays = new ArrayList<>();
  private final List<Reject> rejects = new ArrayList<>();
  private final List<PriceChange> prices = new ArrayList<>();
  private final List<Delay> appliedDelays = new ArrayList<>();

  ScenarioStore(BackendClock clock) {
    clock.onReset(this::clear);
  }

  public synchronized void replace(List<Map<String, Object>> scenarios) {
    clear();
    for (Map<String, Object> s : scenarios) {
      Instant at = Instant.parse(String.valueOf(s.get("at")));
      String supplier = String.valueOf(s.get("supplier"));
      switch (String.valueOf(s.get("type"))) {
        case "supplier_delay" ->
            delays.add(
                new Delay(
                    supplier,
                    at,
                    ((Number) s.get("delay_days")).intValue(),
                    (String) s.get("reason")));
        case "supplier_reject" ->
            rejects.add(
                new Reject(supplier, String.valueOf(s.get("sku")), at, (String) s.get("reason")));
        case "price_change" ->
            prices.add(
                new PriceChange(
                    supplier,
                    String.valueOf(s.get("sku")),
                    at,
                    new BigDecimal(String.valueOf(s.get("unit_cost")))));
        default -> {}
      }
    }
  }

  public synchronized void clear() {
    delays.clear();
    rejects.clear();
    prices.clear();
    appliedDelays.clear();
  }

  /** Retrasos cuya hora ya llegó y que no se han aplicado. */
  public synchronized List<Delay> dueDelays(Instant now) {
    List<Delay> due = new ArrayList<>();
    for (Delay d : delays) {
      if (!d.at().isAfter(now) && !appliedDelays.contains(d)) {
        due.add(d);
        appliedDelays.add(d);
      }
    }
    return due;
  }

  /** Consume el rechazo forzado si la orden lo cumple (una sola vez). */
  public synchronized Optional<Reject> takeReject(String supplier, List<String> skus, Instant now) {
    for (Reject r : rejects) {
      if (r.supplier().equals(supplier) && skus.contains(r.sku()) && !now.isBefore(r.from())) {
        rejects.remove(r);
        return Optional.of(r);
      }
    }
    return Optional.empty();
  }

  public synchronized List<PriceChange> prices(String supplier, Instant now) {
    return prices.stream()
        .filter(p -> p.supplier().equals(supplier) && now != null && !p.at().isAfter(now))
        .toList();
  }
}
