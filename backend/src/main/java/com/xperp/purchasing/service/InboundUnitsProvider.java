package com.xperp.purchasing.service;

import com.xperp.inventory.api.InTransitProvider;
import com.xperp.inventory.api.StockKey;
import com.xperp.purchasing.domain.PurchaseOrder;
import com.xperp.purchasing.domain.PurchaseOrderItem;
import com.xperp.purchasing.repository.PurchaseOrderRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le dice a inventario cuántas unidades vienen en OC abiertas. Es un bean aparte del servicio de
 * compras para no crear un ciclo de dependencias entre beans.
 */
@Component
class InboundUnitsProvider implements InTransitProvider {

  private final PurchaseOrderRepository orders;

  InboundUnitsProvider(PurchaseOrderRepository orders) {
    this.orders = orders;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<StockKey, Integer> inboundUnits() {
    Map<StockKey, Integer> result = new HashMap<>();
    for (PurchaseOrder po : orders.findOpenWithItems(PurchasingServiceImpl.OPEN_STATUSES)) {
      for (PurchaseOrderItem item : po.getItems()) {
        int pending = Math.max(0, item.getQuantity() - item.getQuantityReceived());
        if (pending > 0) {
          result.merge(
              new StockKey(item.getProductId(), po.getWarehouseId()), pending, Integer::sum);
        }
      }
    }
    return result;
  }
}
