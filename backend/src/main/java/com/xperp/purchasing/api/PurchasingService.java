package com.xperp.purchasing.api;

import com.xperp.shared.persistence.PageResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** API pública de compras: proveedores, ofertas y órdenes de compra. */
public interface PurchasingService {

  // Proveedores y ofertas

  SupplierDto createSupplier(SupplierCommand command);

  void setLeadTime(long supplierId, long warehouseId, int leadTimeDays);

  void upsertOffer(
      long supplierId, long productId, BigDecimal unitCost, int minOrderCases, boolean preferred);

  List<SupplierDto> suppliers();

  SupplierDto supplier(long id);

  Optional<SupplierDto> supplierByCode(String code);

  /** Ofertas de todos los proveedores activos para un producto. */
  List<OfferDto> offersForProduct(long productId);

  List<OfferDto> offersForSupplier(long supplierId);

  /** Lee los catálogos de los proveedores y registra los cambios de precio. */
  int syncSupplierPrices();

  // Órdenes de compra

  /** Crea una OC en borrador o pendiente de aprobación. No la envía. */
  PurchaseOrderDto create(CreatePurchaseOrder command);

  /** Envía la OC al proveedor (partners-mock) y compromete presupuesto. */
  PurchaseOrderDto send(long id);

  PurchaseOrderDto cancel(long id, String reason);

  /** Cancela la OC y crea y envía una nueva con otro proveedor por lo pendiente. */
  PurchaseOrderDto reassign(long id, long newSupplierId, String reason, Long aiDecisionId);

  PurchaseOrderDto order(long id);

  Optional<PurchaseOrderDto> byNumber(String number);

  Optional<PurchaseOrderDto> byIdempotencyKey(String key);

  PageResult<PurchaseOrderDto> orders(
      String status, Long supplierId, Long warehouseId, Integer page, Integer size);

  /** Líneas de OC abiertas con lo que falta por recibir. */
  List<OpenLine> openLines();

  boolean hasOpenOrder(long productId, long warehouseId);

  /** Registra la recepción. {@code quantities} por producto; null recibe lo despachado. */
  PurchaseOrderDto receive(long id, Map<Long, Integer> quantities, String receivedBy);

  void linkDecision(long purchaseOrderId, long decisionId);

  byte[] pdf(long id);

  /** Reintenta enviar las OC que no llegaron al proveedor. */
  int retryUnsent();
}
