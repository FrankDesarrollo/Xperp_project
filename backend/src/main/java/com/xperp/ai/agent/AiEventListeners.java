package com.xperp.ai.agent;

import com.xperp.inventory.api.StockKey;
import com.xperp.inventory.events.InventoryCountRecorded;
import com.xperp.inventory.events.StockBelowSafety;
import com.xperp.logistics.events.SalesOrderPacked;
import com.xperp.purchasing.events.PurchaseOrderStatusChanged;
import com.xperp.purchasing.events.SupplierPriceChanged;
import com.xperp.sales.events.SalesOrderCreated;
import com.xperp.sales.events.SalesOrderReleased;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * Disparadores de las decisiones de la IA (AGENTS.md, 7.5 y 8.4). Los listeners corren después del
 * commit y en segundo plano; cada decisión maneja sus propias transacciones a través del {@code
 * ActionGateway}, por eso no abren una transacción propia.
 */
@Component
class AiEventListeners {

  private final FulfillmentAgents fulfillment;
  private final ExceptionAgent exceptions;
  private final InventoryAgents inventory;
  private final AiFacadeImpl facade;

  AiEventListeners(
      FulfillmentAgents fulfillment,
      ExceptionAgent exceptions,
      InventoryAgents inventory,
      AiFacadeImpl facade) {
    this.fulfillment = fulfillment;
    this.exceptions = exceptions;
    this.inventory = inventory;
    this.facade = facade;
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(SalesOrderCreated e) {
    switch (e.status()) {
      case "created" -> fulfillment.fulfill(e.salesOrderId(), "sales_order_created");
      case "on_hold" -> fulfillment.anomaly(e.salesOrderId());
      default -> {}
    }
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(SalesOrderReleased e) {
    fulfillment.fulfill(e.salesOrderId(), "sales_order_released");
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(SalesOrderPacked e) {
    fulfillment.selectCarrier(e.salesOrderId());
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(StockBelowSafety e) {
    facade.urgent(new StockKey(e.productId(), e.warehouseId()));
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(InventoryCountRecorded e) {
    inventory.discrepancy(e);
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(PurchaseOrderStatusChanged e) {
    exceptions.handle(e);
  }

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  void on(SupplierPriceChanged e) {
    inventory.priceChange(e);
  }
}
