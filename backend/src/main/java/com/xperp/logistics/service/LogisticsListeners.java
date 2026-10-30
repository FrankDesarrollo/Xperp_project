package com.xperp.logistics.service;

import com.xperp.logistics.api.CreateTask;
import com.xperp.partners.events.CarrierUpdateReceived;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.events.PurchaseOrderDelivered;
import com.xperp.sales.events.SalesOrderCancelled;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Reacciones de logística a eventos de compras, ventas y transportadoras. */
@Component
class LogisticsListeners {

  private final LogisticsServiceImpl logistics;
  private final PurchasingService purchasing;
  private final Clock clock;

  LogisticsListeners(LogisticsServiceImpl logistics, PurchasingService purchasing, Clock clock) {
    this.logistics = logistics;
    this.purchasing = purchasing;
    this.clock = clock;
  }

  /** Mercancía en el muelle: tarea de recepción para el bodeguero (AGENTS.md, 4.2 paso 10). */
  @ApplicationModuleListener
  void on(PurchaseOrderDelivered event) {
    PurchaseOrderDto po = purchasing.order(event.purchaseOrderId());
    logistics.createTask(
        new CreateTask(
            event.warehouseId(),
            "receive",
            2,
            "Recibir " + po.number(),
            "%s · %d líneas".formatted(po.supplierName(), po.lines().size()),
            null,
            po.id(),
            null,
            null,
            po.number(),
            Instant.now(clock).plus(Duration.ofHours(2)),
            po.aiDecisionId()));
  }

  @ApplicationModuleListener
  void on(SalesOrderCancelled event) {
    logistics.cancelOpenTasksForOrder(event.salesOrderId());
  }

  @ApplicationModuleListener
  void on(CarrierUpdateReceived event) {
    logistics.applyCarrierUpdate(event.trackingNumber(), event.status(), event.occurredAt());
  }
}
