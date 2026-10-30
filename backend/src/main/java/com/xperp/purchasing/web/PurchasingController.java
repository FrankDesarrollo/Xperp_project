package com.xperp.purchasing.web;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.purchasing.api.CreatePurchaseOrder;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.PurchaseLine;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.shared.contract.api.PurchasingApi;
import com.xperp.shared.contract.model.PurchaseOrder;
import com.xperp.shared.contract.model.PurchaseOrderEvent;
import com.xperp.shared.contract.model.PurchaseOrderItem;
import com.xperp.shared.contract.model.PurchaseOrderPage;
import com.xperp.shared.contract.model.PurchaseOrderRequest;
import com.xperp.shared.contract.model.ReasonRequest;
import com.xperp.shared.contract.model.Supplier;
import com.xperp.shared.contract.model.SupplierLeadTime;
import com.xperp.shared.contract.model.SupplierOffer;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.time.SimTime;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PurchasingController implements PurchasingApi {

  private final PurchasingService purchasing;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final UserDirectory users;

  PurchasingController(
      PurchasingService purchasing,
      CatalogService catalog,
      InventoryService inventory,
      UserDirectory users) {
    this.purchasing = purchasing;
    this.catalog = catalog;
    this.inventory = inventory;
    this.users = users;
  }

  @Override
  public ResponseEntity<List<Supplier>> listSuppliers() {
    return ResponseEntity.ok(purchasing.suppliers().stream().map(this::toModel).toList());
  }

  @Override
  public ResponseEntity<List<SupplierOffer>> listSupplierOffers(Long id) {
    return ResponseEntity.ok(purchasing.offersForSupplier(id).stream().map(this::toModel).toList());
  }

  @Override
  public ResponseEntity<List<SupplierOffer>> listProductOffers(Long productId) {
    return ResponseEntity.ok(
        purchasing.offersForProduct(productId).stream().map(this::toModel).toList());
  }

  @Override
  public ResponseEntity<PurchaseOrderPage> listPurchaseOrders(
      String status, Long supplierId, Long warehouseId, Integer page, Integer size) {
    PageResult<PurchaseOrder> result =
        purchasing.orders(status, supplierId, warehouseId, page, size).map(o -> toModel(o, false));
    return ResponseEntity.ok(
        new PurchaseOrderPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','purchasing','manager')")
  public ResponseEntity<PurchaseOrder> createPurchaseOrder(PurchaseOrderRequest request) {
    PurchaseOrderDto po =
        purchasing.create(
            new CreatePurchaseOrder(
                request.getSupplierId(),
                request.getWarehouseId(),
                request.getItems().stream()
                    .map(i -> new PurchaseLine(i.getProductId(), i.getQuantity(), null))
                    .toList(),
                CurrentUser.idOrNull(),
                null,
                null,
                null,
                false,
                "OC manual creada por " + CurrentUser.nameOr("un usuario")));
    if (Boolean.TRUE.equals(request.getSend())) {
      po = purchasing.send(po.id());
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(toModel(po, true));
  }

  @Override
  public ResponseEntity<PurchaseOrder> getPurchaseOrder(Long id) {
    return ResponseEntity.ok(toModel(purchasing.order(id), true));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','purchasing','manager')")
  public ResponseEntity<PurchaseOrder> sendPurchaseOrder(Long id) {
    return ResponseEntity.ok(toModel(purchasing.send(id), true));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','purchasing','manager')")
  public ResponseEntity<PurchaseOrder> cancelPurchaseOrder(Long id, ReasonRequest reason) {
    String text = reason == null || reason.getReason() == null ? null : reason.getReason();
    return ResponseEntity.ok(
        toModel(
            purchasing.cancel(
                id, text == null ? "Cancelada por " + CurrentUser.nameOr("un usuario") : text),
            true));
  }

  @Override
  public ResponseEntity<Resource> getPurchaseOrderPdf(Long id) {
    PurchaseOrderDto po = purchasing.order(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + po.number() + ".pdf\"")
        .body(new ByteArrayResource(purchasing.pdf(id)));
  }

  // ---------------------------------------------------------------- mapeo

  private Supplier toModel(SupplierDto s) {
    List<SupplierLeadTime> times =
        s.leadTimes().entrySet().stream()
            .map(
                e ->
                    new SupplierLeadTime(
                        e.getKey(), inventory.warehouse(e.getKey()).code(), e.getValue()))
            .sorted((a, b) -> a.getWarehouseCode().compareTo(b.getWarehouseCode()))
            .toList();
    return new Supplier(
        s.id(),
        s.code(),
        s.name(),
        s.nit(),
        s.city(),
        s.email(),
        Money.format(s.minOrderAmount()),
        s.paymentTermsDays(),
        s.onTimeRate(),
        s.fillRate(),
        s.active(),
        times,
        purchasing.offersForSupplier(s.id()).size());
  }

  private SupplierOffer toModel(OfferDto o) {
    ProductDto p = catalog.product(o.productId());
    return new SupplierOffer(
            o.supplier().id(),
            o.supplier().code(),
            o.supplier().name(),
            p.id(),
            p.sku(),
            p.name(),
            p.categoryCode(),
            Money.format(o.unitCost()),
            Money.format(p.cost()),
            o.minOrderCases(),
            p.unitsPerCase(),
            o.preferred())
        .priceUpdatedAt(SimTime.toOffset(o.priceUpdatedAt()));
  }

  private PurchaseOrder toModel(PurchaseOrderDto po, boolean detail) {
    PurchaseOrder model =
        new PurchaseOrder(
                po.id(),
                po.number(),
                po.supplierId(),
                po.supplierCode(),
                po.supplierName(),
                po.warehouseId(),
                inventory.warehouse(po.warehouseId()).code(),
                po.status(),
                Money.format(po.subtotal()),
                Money.format(po.total()),
                SimTime.toOffset(po.createdAt()),
                po.createdByUserId() == null)
            .statusReason(po.statusReason())
            .expectedAt(SimTime.toOffset(po.expectedAt()))
            .sentAt(SimTime.toOffset(po.sentAt()))
            .confirmedAt(SimTime.toOffset(po.confirmedAt()))
            .receivedAt(SimTime.toOffset(po.receivedAt()))
            .createdByUserId(po.createdByUserId())
            .createdByName(users.nameOf(po.createdByUserId()))
            .aiDecisionId(po.aiDecisionId())
            .replacesPoId(po.replacesPoId())
            .externalId(po.externalId());
    if (po.replacesPoId() != null) {
      model.setReplacesPoNumber(purchasing.order(po.replacesPoId()).number());
    }
    model.setItems(
        po.lines().stream()
            .map(
                l -> {
                  ProductDto p = catalog.product(l.productId());
                  return new PurchaseOrderItem(
                      l.id(),
                      l.productId(),
                      p.sku(),
                      p.name(),
                      l.quantity(),
                      l.quantityReceived(),
                      Money.format(l.unitCost()),
                      Money.format(l.lineTotal()));
                })
            .toList());
    if (detail) {
      model.setEvents(
          po.events().stream()
              .map(
                  e ->
                      new PurchaseOrderEvent(e.status(), SimTime.toOffset(e.occurredAt()))
                          .note(e.note()))
              .toList());
    }
    return model;
  }
}
