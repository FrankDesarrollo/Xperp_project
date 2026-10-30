package com.xperp.partners.service;

import com.xperp.partners.events.CarrierUpdateReceived;
import com.xperp.partners.events.SupplierUpdateReceived;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica los webhooks como eventos dentro de una transacción, para que Spring Modulith los
 * persista en event_publication y los entregue después del commit.
 */
@Service
public class WebhookService {

  private final ApplicationEventPublisher events;

  WebhookService(ApplicationEventPublisher events) {
    this.events = events;
  }

  @Transactional
  public void supplierUpdate(SupplierUpdateReceived event) {
    events.publishEvent(event);
  }

  @Transactional
  public void carrierUpdate(CarrierUpdateReceived event) {
    events.publishEvent(event);
  }
}
