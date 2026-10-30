package com.xperp.inventory.web;

import com.xperp.catalog.api.CatalogService;
import com.xperp.inventory.api.CountDto;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.MovementDto;
import com.xperp.inventory.api.TransferDto;
import com.xperp.inventory.api.TransferLine;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.shared.contract.api.InventoryApi;
import com.xperp.shared.contract.model.InventoryAdjustmentRequest;
import com.xperp.shared.contract.model.InventoryCount;
import com.xperp.shared.contract.model.InventoryCountRequest;
import com.xperp.shared.contract.model.InventoryItem;
import com.xperp.shared.contract.model.InventoryMovement;
import com.xperp.shared.contract.model.InventoryMovementPage;
import com.xperp.shared.contract.model.InventoryPage;
import com.xperp.shared.contract.model.StockTransfer;
import com.xperp.shared.contract.model.StockTransferItem;
import com.xperp.shared.contract.model.StockTransferPage;
import com.xperp.shared.contract.model.StockTransferRequest;
import com.xperp.shared.contract.model.Warehouse;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.AuthenticatedUser;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.time.SimTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InventoryController implements InventoryApi {

  private final InventoryService inventory;
  private final CatalogService catalog;

  InventoryController(InventoryService inventory, CatalogService catalog) {
    this.inventory = inventory;
    this.catalog = catalog;
  }

  @Override
  public ResponseEntity<List<Warehouse>> listWarehouses() {
    return ResponseEntity.ok(
        inventory.warehouses().stream().map(InventoryController::toModel).toList());
  }

  @Override
  public ResponseEntity<InventoryPage> listInventory(
      Long warehouseId, String status, String q, String categoryCode, Integer page, Integer size) {
    String query = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
    List<InventoryRow> rows =
        inventory.rows(warehouseId).stream()
            .filter(r -> matchesStatus(r, status))
            .filter(
                r ->
                    categoryCode == null
                        || categoryCode.isBlank()
                        || r.categoryCode().equals(categoryCode))
            .filter(
                r ->
                    query == null
                        || query.isEmpty()
                        || r.sku().toLowerCase(Locale.ROOT).contains(query)
                        || r.productName().toLowerCase(Locale.ROOT).contains(query))
            .toList();
    PageResult<InventoryItem> result =
        PageResult.slice(rows, page, size).map(InventoryController::toModel);
    return ResponseEntity.ok(
        new InventoryPage(result.items(), result.total(), result.page(), result.size()));
  }

  private static boolean matchesStatus(InventoryRow r, String status) {
    if (status == null || status.isBlank()) {
      return true;
    }
    if ("low".equals(status)) {
      return InventoryRow.LOW.equals(r.status()) || InventoryRow.STOCKOUT.equals(r.status());
    }
    return status.equals(r.status());
  }

  @Override
  public ResponseEntity<InventoryItem> getInventoryItem(Long productId, Long warehouseId) {
    return ResponseEntity.ok(toModel(inventory.row(productId, warehouseId)));
  }

  @Override
  public ResponseEntity<InventoryMovementPage> listMovements(
      Long productId, Long warehouseId, Integer page, Integer size) {
    PageResult<InventoryMovement> result =
        inventory.movements(productId, warehouseId, page, size).map(InventoryController::toModel);
    return ResponseEntity.ok(
        new InventoryMovementPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager')")
  public ResponseEntity<InventoryMovement> createAdjustment(InventoryAdjustmentRequest request) {
    MovementDto m =
        inventory.adjust(
            request.getProductId(),
            request.getWarehouseId(),
            request.getQuantity(),
            request.getReason(),
            "manual_adjustment",
            null);
    return ResponseEntity.status(HttpStatus.CREATED).body(toModel(m));
  }

  @Override
  public ResponseEntity<List<InventoryCount>> listCounts(Long warehouseId) {
    return ResponseEntity.ok(
        inventory.recentCounts(warehouseId).stream().map(this::toModel).toList());
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','warehouse')")
  public ResponseEntity<InventoryCount> createCount(InventoryCountRequest request) {
    AuthenticatedUser user = CurrentUser.require();
    if (user.hasRole("warehouse")
        && user.warehouseId() != null
        && !user.warehouseId().equals(request.getWarehouseId())) {
      throw BusinessException.forbidden("Solo puedes contar en tu bodega");
    }
    CountDto count =
        inventory.recordCount(
            request.getProductId(), request.getWarehouseId(), request.getCountedQty(), false);
    return ResponseEntity.status(HttpStatus.CREATED).body(toModel(count));
  }

  @Override
  public ResponseEntity<StockTransferPage> listStockTransfers(
      String status, Integer page, Integer size) {
    PageResult<StockTransfer> result = inventory.transfers(status, page, size).map(this::toModel);
    return ResponseEntity.ok(
        new StockTransferPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager')")
  public ResponseEntity<StockTransfer> createStockTransfer(StockTransferRequest request) {
    TransferDto t =
        inventory.createTransfer(
            request.getFromWarehouseId(),
            request.getToWarehouseId(),
            request.getItems().stream()
                .map(i -> new TransferLine(i.getProductId(), i.getQuantity()))
                .toList(),
            null);
    return ResponseEntity.status(HttpStatus.CREATED).body(toModel(t));
  }

  // ---------------------------------------------------------------- mapeo

  static Warehouse toModel(WarehouseDto w) {
    return new Warehouse(w.id(), w.code(), w.name(), w.city()).address(w.address());
  }

  static InventoryItem toModel(InventoryRow r) {
    var s = r.stock();
    return new InventoryItem(
            s.productId(),
            r.sku(),
            r.productName(),
            r.categoryCode(),
            r.unitsPerCase(),
            s.warehouseId(),
            r.warehouseCode(),
            s.onHand(),
            s.reserved(),
            s.available(),
            r.inTransit(),
            s.safetyStock(),
            s.reorderPoint(),
            s.maxStock(),
            s.coverDays(),
            s.avgDailyDemand().doubleValue(),
            r.status(),
            Money.format(r.unitCost()),
            Money.format(r.stockValue()),
            s.promotionSuggested(),
            s.reorderBlocked())
        .daysOfCover(s.daysOfCover() == null ? null : Math.round(s.daysOfCover() * 10) / 10.0)
        .paramsUpdatedAt(SimTime.toOffset(s.paramsUpdatedAt()));
  }

  static InventoryMovement toModel(MovementDto m) {
    return new InventoryMovement(
            m.id(), m.type(), m.quantity(), m.balanceAfter(), SimTime.toOffset(m.occurredAt()))
        .unitCost(Money.format(m.unitCost()))
        .referenceType(m.referenceType())
        .referenceId(m.referenceId())
        .note(m.note())
        .createdBy(m.createdBy());
  }

  InventoryCount toModel(CountDto c) {
    return new InventoryCount(
            c.id(),
            c.productId(),
            catalog.product(c.productId()).sku(),
            c.warehouseId(),
            inventory.warehouse(c.warehouseId()).code(),
            c.systemQty(),
            c.countedQty(),
            c.difference(),
            Math.round(c.differencePct() * 100) / 100.0,
            c.status(),
            SimTime.toOffset(c.countedAt()))
        .countedBy(c.countedBy());
  }

  StockTransfer toModel(TransferDto t) {
    Map<Long, String> codes = new HashMap<>();
    inventory.warehouses().forEach(w -> codes.put(w.id(), w.code()));
    List<StockTransferItem> items =
        t.lines().stream()
            .map(
                l -> {
                  var p = catalog.product(l.productId());
                  return new StockTransferItem(p.id(), p.sku(), p.name(), l.quantity());
                })
            .toList();
    return new StockTransfer(
            t.id(),
            t.number(),
            t.fromWarehouseId(),
            codes.get(t.fromWarehouseId()),
            t.toWarehouseId(),
            codes.get(t.toWarehouseId()),
            t.status(),
            SimTime.toOffset(t.createdAt()),
            items)
        .aiDecisionId(t.aiDecisionId())
        .shippedAt(SimTime.toOffset(t.shippedAt()))
        .receivedAt(SimTime.toOffset(t.receivedAt()));
  }
}
