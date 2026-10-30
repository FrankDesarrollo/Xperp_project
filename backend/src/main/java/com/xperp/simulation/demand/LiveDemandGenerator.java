package com.xperp.simulation.demand;

import com.xperp.sales.api.CreateSalesOrder;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.config.SimulationSettings;
import com.xperp.shared.time.SimClock;
import com.xperp.shared.time.SimTime;
import com.xperp.simulation.seed.DemoContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Generador de demanda en vivo (AGENTS.md, 10.2): crea los pedidos de venta a través de {@link
 * SalesService}, como lo haría cualquier cliente, con la hora simulada de cada pedido.
 */
@Component
public class LiveDemandGenerator implements ResettableCache {

  private static final Logger log = LoggerFactory.getLogger(LiveDemandGenerator.class);

  private final DemoContext context;
  private final SalesService sales;
  private final SimClock clock;
  private final SimulationSettings settings;
  private final Map<LocalDate, List<OrderPlanner.PlannedOrder>> plans = new ConcurrentHashMap<>();

  LiveDemandGenerator(
      DemoContext context, SalesService sales, SimClock clock, SimulationSettings settings) {
    this.context = context;
    this.sales = sales;
    this.clock = clock;
    this.settings = settings;
  }

  /** Crea los pedidos con hora en (from, to]. Deja el reloj en cada hora de pedido. */
  public int generate(Instant from, Instant to) {
    int created = 0;
    for (LocalDate d = SimTime.dateOf(from); !d.isAfter(SimTime.dateOf(to)); d = d.plusDays(1)) {
      for (OrderPlanner.PlannedOrder o : plan(d)) {
        if (o.at().isAfter(from) && !o.at().isAfter(to)) {
          clock.set(o.at());
          try {
            sales.create(new CreateSalesOrder(o.customerId(), o.lines()));
            created++;
          } catch (RuntimeException e) {
            log.warn("No se pudo crear un pedido simulado: {}", e.getMessage());
          }
        }
      }
    }
    plans.keySet().removeIf(d -> d.isBefore(SimTime.dateOf(from).minusDays(1)));
    return created;
  }

  private List<OrderPlanner.PlannedOrder> plan(LocalDate date) {
    return plans.computeIfAbsent(
        date,
        d -> {
          List<OrderPlanner.PlannedOrder> all = new ArrayList<>();
          for (DemoContext.WarehouseDemand w : context.snapshot().warehouses()) {
            all.addAll(context.plan(d, w, settings.randomSeed()));
          }
          all.sort(Comparator.comparing(OrderPlanner.PlannedOrder::at));
          return all;
        });
  }

  @Override
  public void clearCache() {
    plans.clear();
  }
}
