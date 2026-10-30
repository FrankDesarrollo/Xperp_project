package com.xperp.simulation.demand;

import com.xperp.sales.api.SalesLine;
import com.xperp.shared.time.SimTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Convierte la demanda diaria por SKU en pedidos de clientes (AGENTS.md, 2.8 y 2.10). Lo usan tanto
 * el historial de 90 días como el generador en vivo, así que el patrón es el mismo. Con la misma
 * semilla, el resultado es siempre el mismo.
 */
public final class OrderPlanner {

  public record ProductSpec(
      long productId, String sku, String category, int unitsPerCase, double baseDemand) {}

  public record CustomerSpec(long id, String segment) {}

  public record SegmentSpec(
      String code, int linesMin, int linesMax, int qtyMin, int qtyMax, boolean cases) {}

  public record PlannedOrder(Instant at, long customerId, List<SalesLine> lines) {}

  private record Draft(
      Instant at, CustomerSpec customer, SegmentSpec segment, Map<Long, Double> weights) {}

  private OrderPlanner() {}

  public static List<PlannedOrder> plan(
      LocalDate date,
      String warehouseCode,
      double warehouseFactor,
      List<ProductSpec> products,
      List<CustomerSpec> customers,
      Map<String, SegmentSpec> segments,
      DemandProfile profile,
      Set<LocalDate> holidays,
      long seed) {
    if (customers.isEmpty() || products.isEmpty()) {
      return List.of();
    }
    Random rng = new Random(seed * 1_000_003L + date.toEpochDay() * 31 + warehouseCode.hashCode());

    // 1. Demanda del día por SKU
    Map<Long, Integer> demand = new LinkedHashMap<>();
    Map<Long, ProductSpec> byId = new HashMap<>();
    for (ProductSpec p : products) {
      byId.put(p.productId(), p);
      demand.put(
          p.productId(),
          DemandModel.units(
              p.baseDemand(), warehouseFactor, date, p.category(), profile, holidays, rng));
    }

    // 2. Pedidos del día, repartidos en las horas con picos
    double dayFactor = DemandModel.dayFactor(date, profile, holidays);
    int baseOrders = profile.ordersPerDay().getOrDefault(warehouseCode, 40);
    int count =
        Math.max(1, (int) Math.round(baseOrders * dayFactor * DemandModel.noise(rng, 0.08)));
    List<Draft> drafts = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CustomerSpec c = customers.get(rng.nextInt(customers.size()));
      SegmentSpec s = segments.get(c.segment());
      int hour = pickHour(profile.hourlyWeights(), rng);
      int minute = rng.nextInt(60);
      int second = rng.nextInt(60);
      Instant at = date.atTime(hour, minute, second).atZone(SimTime.ZONE).toInstant();
      drafts.add(new Draft(at, c, s, new LinkedHashMap<>()));
    }

    // 3. Qué SKU lleva cada pedido: muestreo ponderado por la demanda del día
    for (Draft d : drafts) {
      int lines =
          d.segment().linesMin() + rng.nextInt(d.segment().linesMax() - d.segment().linesMin() + 1);
      lines = Math.min(lines, products.size());
      Set<Long> chosen = pickWeighted(demand, lines, rng);
      for (Long productId : chosen) {
        ProductSpec p = byId.get(productId);
        int qty =
            d.segment().qtyMin() + rng.nextInt(d.segment().qtyMax() - d.segment().qtyMin() + 1);
        double weight = d.segment().cases() ? qty * p.unitsPerCase() : qty;
        d.weights().put(productId, weight);
      }
    }

    // 4. La demanda de cada SKU se reparte entre las líneas que lo incluyen. Cada línea tiene un
    // tope (2,5 veces lo típico de su segmento) para que un cliente no pida de golpe diez veces lo
    // normal; lo que sobra se reparte en otros pedidos del día.
    double totalWeight = 0;
    for (Draft d : drafts) {
      for (double w : d.weights().values()) {
        totalWeight += w;
      }
    }
    int totalDemand = demand.values().stream().mapToInt(Integer::intValue).sum();
    double scale = totalWeight <= 0 ? 1 : totalDemand / totalWeight;
    List<Map<Long, Integer>> quantities = new ArrayList<>();
    drafts.forEach(d -> quantities.add(new LinkedHashMap<>()));
    for (Map.Entry<Long, Integer> e : demand.entrySet()) {
      long productId = e.getKey();
      ProductSpec product = byId.get(productId);
      int total = e.getValue();
      if (total <= 0) {
        continue;
      }
      List<Integer> holders = new ArrayList<>();
      double sum = 0;
      for (int i = 0; i < drafts.size(); i++) {
        Double w = drafts.get(i).weights().get(productId);
        if (w != null) {
          holders.add(i);
          sum += w;
        }
      }
      int assigned = 0;
      for (int i : holders) {
        double w = drafts.get(i).weights().get(productId);
        int cap = Math.max(1, (int) Math.ceil(w * scale * 2.5));
        int q = Math.min(cap, (int) Math.floor(total * w / sum));
        if (q > 0) {
          quantities.get(i).put(productId, q);
          assigned += q;
        }
      }
      int guard = 0;
      while (assigned < total && guard++ < drafts.size() * 4) {
        int i = rng.nextInt(drafts.size());
        Draft d = drafts.get(i);
        int current = quantities.get(i).getOrDefault(productId, 0);
        double w =
            d.weights()
                .computeIfAbsent(
                    productId,
                    k -> {
                      int qty =
                          d.segment().qtyMin()
                              + rng.nextInt(d.segment().qtyMax() - d.segment().qtyMin() + 1);
                      return d.segment().cases() ? (double) qty * product.unitsPerCase() : qty;
                    });
        int cap = Math.max(1, (int) Math.ceil(w * scale * 2.5));
        int add = Math.min(total - assigned, Math.max(0, cap - current));
        if (add > 0) {
          quantities.get(i).put(productId, current + add);
          assigned += add;
        }
      }
    }

    List<PlannedOrder> result = new ArrayList<>();
    for (int i = 0; i < drafts.size(); i++) {
      Map<Long, Integer> q = quantities.get(i);
      if (q.isEmpty()) {
        continue;
      }
      List<SalesLine> lines = new ArrayList<>();
      q.forEach((productId, qty) -> lines.add(new SalesLine(productId, qty)));
      result.add(new PlannedOrder(drafts.get(i).at(), drafts.get(i).customer().id(), lines));
    }
    result.sort(Comparator.comparing(PlannedOrder::at));
    return result;
  }

  static int pickHour(Map<Integer, Double> weights, Random rng) {
    double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
    double r = rng.nextDouble() * total;
    for (Map.Entry<Integer, Double> e : weights.entrySet()) {
      r -= e.getValue();
      if (r <= 0) {
        return e.getKey();
      }
    }
    return 12;
  }

  static Set<Long> pickWeighted(Map<Long, Integer> weights, int n, Random rng) {
    Set<Long> chosen = new HashSet<>();
    List<Long> ids = new ArrayList<>(weights.keySet());
    int guard = 0;
    while (chosen.size() < n && guard++ < n * 20) {
      double total = 0;
      for (Long id : ids) {
        if (!chosen.contains(id)) {
          total += Math.max(1, weights.get(id));
        }
      }
      double r = rng.nextDouble() * total;
      for (Long id : ids) {
        if (chosen.contains(id)) {
          continue;
        }
        r -= Math.max(1, weights.get(id));
        if (r <= 0) {
          chosen.add(id);
          break;
        }
      }
    }
    return chosen;
  }
}
