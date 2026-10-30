package com.xperp.purchasing.service;

import com.xperp.partners.events.SupplierUpdateReceived;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Aplica a la OC lo que el proveedor respondió por webhook. */
@Component
class SupplierUpdateListener {

  private final PurchasingServiceImpl purchasing;

  SupplierUpdateListener(PurchasingServiceImpl purchasing) {
    this.purchasing = purchasing;
  }

  @ApplicationModuleListener
  void on(SupplierUpdateReceived event) {
    Map<String, Integer> shipped = new LinkedHashMap<>();
    if (event.items() != null) {
      event.items().forEach(i -> shipped.put(i.sku(), i.quantity()));
    }
    purchasing.applySupplierUpdate(
        event.poNumber(),
        event.externalId(),
        event.status(),
        event.expectedAt(),
        event.reason(),
        shipped);
  }
}
