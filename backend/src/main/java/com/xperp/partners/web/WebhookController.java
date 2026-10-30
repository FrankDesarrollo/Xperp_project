package com.xperp.partners.web;

import com.xperp.partners.events.CarrierUpdateReceived;
import com.xperp.partners.events.SupplierUpdateReceived;
import com.xperp.partners.service.WebhookService;
import com.xperp.shared.contract.api.WebhooksApi;
import com.xperp.shared.contract.model.CarrierWebhook;
import com.xperp.shared.contract.model.SupplierWebhook;
import com.xperp.shared.tenancy.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe las respuestas de proveedores y transportadoras. La firma ya la verificó {@link
 * WebhookSignatureFilter}. Compras y logística aplican los cambios al consumir los eventos.
 */
@RestController
class WebhookController implements WebhooksApi {

  private final WebhookService webhooks;

  WebhookController(WebhookService webhooks) {
    this.webhooks = webhooks;
  }

  @Override
  public ResponseEntity<Void> receiveSupplierWebhook(String signature, SupplierWebhook body) {
    webhooks.supplierUpdate(
        new SupplierUpdateReceived(
            TenantContext.require(),
            body.getExternalId(),
            body.getPoNumber(),
            body.getStatus(),
            body.getExpectedAt() == null ? null : body.getExpectedAt().toInstant(),
            body.getReason(),
            body.getItems().stream()
                .map(i -> new SupplierUpdateReceived.Item(i.getSku(), i.getQuantity()))
                .toList()));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> receiveCarrierWebhook(String signature, CarrierWebhook body) {
    webhooks.carrierUpdate(
        new CarrierUpdateReceived(
            TenantContext.require(),
            body.getTrackingNumber(),
            body.getStatus(),
            body.getOccurredAt().toInstant()));
    return ResponseEntity.noContent().build();
  }
}
