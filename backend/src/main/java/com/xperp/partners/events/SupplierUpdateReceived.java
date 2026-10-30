package com.xperp.partners.events;

import java.time.Instant;
import java.util.List;

/** Un proveedor respondió por webhook sobre una OC (AGENTS.md, sección 7.3). */
public record SupplierUpdateReceived(
    long companyId,
    String externalId,
    String poNumber,
    String status,
    Instant expectedAt,
    String reason,
    List<Item> items) {

  public record Item(String sku, int quantity) {}
}
