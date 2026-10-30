package com.xperp.partners.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Proveedores simulados (contracts/partners.yaml). */
public interface SupplierGateway {

  record OrderLine(String sku, int quantity, BigDecimal unitCost) {}

  record CatalogPrice(String sku, BigDecimal unitCost, Instant validFrom) {}

  /** Envía una OC. Devuelve el id externo del proveedor. */
  String sendOrder(
      String supplierCode, String poNumber, String warehouseCode, List<OrderLine> lines);

  /** Precios vigentes que el proveedor publicó (solo los que cambiaron). */
  List<CatalogPrice> catalog(String supplierCode);
}
