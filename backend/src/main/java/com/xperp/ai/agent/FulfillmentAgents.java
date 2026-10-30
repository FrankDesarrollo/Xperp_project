package com.xperp.ai.agent;

import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.scoring.CarrierScorer;
import com.xperp.inventory.api.InventoryService;
import com.xperp.logistics.api.CarrierQuoteDto;
import com.xperp.logistics.api.LogisticsService;
import com.xperp.logistics.api.ShipmentContext;
import com.xperp.sales.api.SalesOrderDto;
import com.xperp.sales.api.SalesOrderLineDto;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.security.UserDirectory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Alistamiento (fulfillment, nivel 3), retención por anomalía (demand_anomaly, nivel 2) y selección
 * de transportadora (carrier_selection, nivel 2). AGENTS.md, 4.1 y 8.4.
 */
@Component
class FulfillmentAgents {

  private final SalesService sales;
  private final LogisticsService logistics;
  private final InventoryService inventory;
  private final UserDirectory users;
  private final LlmGateway llm;
  private final ActionGateway gateway;

  FulfillmentAgents(
      SalesService sales,
      LogisticsService logistics,
      InventoryService inventory,
      UserDirectory users,
      LlmGateway llm,
      ActionGateway gateway) {
    this.sales = sales;
    this.logistics = logistics;
    this.inventory = inventory;
    this.users = users;
    this.llm = llm;
    this.gateway = gateway;
  }

  /** Pedido creado o liberado: tareas de alistamiento y empaque para el bodeguero. */
  void fulfill(long salesOrderId, String trigger) {
    SalesOrderDto order = sales.order(salesOrderId);
    if (!"created".equals(order.status())) {
      return;
    }
    int units = order.lines().stream().mapToInt(SalesOrderLineDto::quantityFulfilled).sum();
    int partial =
        (int) order.lines().stream().filter(l -> l.quantityFulfilled() < l.quantity()).count();
    String warehouse = inventory.warehouse(order.warehouseId()).code();
    String keeper =
        users
            .warehouseKeeper(order.warehouseId())
            .map(UserDirectory.UserRecord::fullName)
            .orElse("la bodega");
    String reasoning =
        "Pedido %s de %s: %d líneas y %s und reservadas en %s%s. Se crean las tareas de alistamiento y empaque para %s."
            .formatted(
                order.number(),
                order.customerName(),
                order.lines().size(),
                Fmt.units(units),
                warehouse,
                partial > 0 ? " (" + partial + " líneas parciales por falta de disponible)" : "",
                keeper);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("pedido", order.number());
    input.put("cliente", order.customerName());
    input.put("bodega", warehouse);
    input.put("lineas", order.lines().size());
    input.put("unidades", units);
    input.put("lineas_parciales", partial);
    gateway.submit(
        ActionProposal.builder("fulfillment", "create_warehouse_task")
            .title("Alistar " + order.number())
            .trigger(trigger, order.number())
            .input(input)
            .payload(Map.of("sales_order_id", order.id(), "type", "pick_pack"))
            .reasoning(reasoning)
            .ref("sales_order", order.id(), order.number())
            .build());
  }

  /** Pedido retenido por el filtro de anomalías: queda registrado y con alerta. */
  void anomaly(long salesOrderId) {
    SalesOrderDto order = sales.order(salesOrderId);
    String facts =
        "Pedido %s de %s retenido antes de reservar stock: %s. No se trata como demanda real hasta que alguien lo revise y lo libere."
            .formatted(order.number(), order.customerName(), order.holdReason());
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("order", order.number());
    vars.put("customer", order.customerName());
    vars.put("facts", facts);
    LlmGateway.Text text = llm.write("anomaly", vars, facts);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("pedido", order.number());
    input.put("cliente", order.customerName());
    input.put("segmento", order.customerSegment());
    input.put("motivo", order.holdReason());
    input.put("total", Money.format(order.total()));
    gateway.submit(
        ActionProposal.builder("demand_anomaly", "hold_sales_order")
            .title("Retener " + order.number() + " por anomalía")
            .trigger("sales_order_created", order.number())
            .input(input)
            .alternatives(
                List.of(
                    Map.of(
                        "opcion",
                        "retener",
                        "nota",
                        "revisión humana antes de tratarlo como demanda"),
                    Map.of("opcion", "despachar", "nota", "consumiría stock de otros clientes")))
            .payload(Map.of("sales_order_id", order.id()))
            .reasoning(text.text())
            .confidence(0.8)
            .llm(text.trace())
            .ref("sales_order", order.id(), order.number())
            .build());
  }

  /** Pedido empacado: cotiza, elige transportadora y genera la guía. */
  void selectCarrier(long salesOrderId) {
    SalesOrderDto order = sales.order(salesOrderId);
    if (!"packed".equals(order.status()) || logistics.hasShipment(salesOrderId)) {
      return;
    }
    List<CarrierQuoteDto> quotes = logistics.quote(salesOrderId);
    if (quotes.isEmpty()) {
      return; // se reintenta en el mantenimiento horario
    }
    ShipmentContext ctx = logistics.shipmentContext(salesOrderId);
    List<CarrierScorer.Candidate> candidates = new ArrayList<>();
    for (CarrierQuoteDto q : quotes) {
      candidates.add(
          new CarrierScorer.Candidate(
              q.carrier().id(),
              q.carrier().code(),
              q.carrier().name(),
              q.service(),
              q.cost().doubleValue(),
              q.etaDays(),
              q.carrier().onTimeRate()));
    }
    List<CarrierScorer.Scored> scored = CarrierScorer.score(candidates);
    CarrierScorer.Scored best = scored.get(0);
    String reasoning =
        "%s a %s (%s kg): %s gana con %s puntos, %s y entrega en %d %s%s."
            .formatted(
                order.number(),
                ctx.destinationCity(),
                Fmt.dec(ctx.weightKg()),
                best.candidate().carrierName(),
                Fmt.score(best.total()),
                Fmt.money(BigDecimal.valueOf(best.candidate().cost())),
                best.candidate().etaDays(),
                best.candidate().etaDays() == 1 ? "día" : "días",
                scored.size() > 1
                    ? " frente a "
                        + scored.get(1).candidate().carrierName()
                        + " ("
                        + Fmt.score(scored.get(1).total())
                        + ")"
                    : ", la única que cubre la ciudad");
    int winner = 0;
    ActionProposal.LlmTrace trace = ActionProposal.LlmTrace.NONE;
    if (CarrierScorer.isTie(scored)) {
      List<LlmGateway.Option> options = new ArrayList<>();
      for (CarrierScorer.Scored s : scored) {
        options.add(
            new LlmGateway.Option(
                s.candidate().carrierCode(),
                s.candidate().carrierName(),
                Map.of(
                    "costo",
                    s.candidate().cost(),
                    "entrega_dias",
                    s.candidate().etaDays(),
                    "cumplimiento",
                    s.candidate().onTimeRate(),
                    "puntaje",
                    s.total())));
      }
      LlmGateway.Choice choice =
          llm.choose(
              "carrier",
              Map.of(
                  "order", order.number(), "city", ctx.destinationCity(), "weight", ctx.weightKg()),
              options,
              0,
              "Empate técnico entre transportadoras. " + reasoning);
      winner = choice.index();
      reasoning = choice.reasoning();
      trace = choice.trace();
    }
    CarrierScorer.Scored chosen = scored.get(winner);
    List<Map<String, Object>> alternatives = new ArrayList<>();
    for (CarrierScorer.Scored s : scored) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("transportadora", s.candidate().carrierCode());
      m.put("servicio", s.candidate().service());
      m.put("costo", s.candidate().cost());
      m.put("entrega_dias", s.candidate().etaDays());
      m.put("cumplimiento", s.candidate().onTimeRate());
      m.put("puntaje", s.total());
      alternatives.add(m);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sales_order_id", order.id());
    payload.put("carrier_id", chosen.candidate().carrierId());
    payload.put("carrier_code", chosen.candidate().carrierCode());
    payload.put("service", chosen.candidate().service());
    payload.put("cost", Money.format(BigDecimal.valueOf(chosen.candidate().cost())));
    payload.put("eta_days", chosen.candidate().etaDays());
    gateway.submit(
        ActionProposal.builder("carrier_selection", "create_shipment")
            .title("Despachar " + order.number() + " con " + chosen.candidate().carrierCode())
            .trigger("sales_order_packed", order.number())
            .input(
                Map.of(
                    "pedido", order.number(),
                    "origen", ctx.originCity(),
                    "destino", ctx.destinationCity(),
                    "peso_kg", ctx.weightKg()))
            .alternatives(alternatives)
            .payload(payload)
            .reasoning(reasoning)
            .amount(Money.round(BigDecimal.valueOf(chosen.candidate().cost())))
            .llm(trace)
            .ref("sales_order", order.id(), order.number())
            .build());
  }
}
