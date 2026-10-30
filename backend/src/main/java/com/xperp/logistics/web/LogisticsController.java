package com.xperp.logistics.web;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.logistics.api.CarrierDto;
import com.xperp.logistics.api.LogisticsService;
import com.xperp.logistics.api.ShipmentDto;
import com.xperp.logistics.api.TaskCompletion;
import com.xperp.logistics.api.TaskDto;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.contract.api.LogisticsApi;
import com.xperp.shared.contract.model.Carrier;
import com.xperp.shared.contract.model.Shipment;
import com.xperp.shared.contract.model.ShipmentPage;
import com.xperp.shared.contract.model.TaskCompletionRequest;
import com.xperp.shared.contract.model.TaskLine;
import com.xperp.shared.contract.model.WarehouseTask;
import com.xperp.shared.contract.model.WarehouseTaskPage;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.AuthenticatedUser;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.time.SimTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LogisticsController implements LogisticsApi {

  private final LogisticsService logistics;
  private final InventoryService inventory;
  private final CatalogService catalog;
  private final SalesService sales;
  private final UserDirectory users;

  LogisticsController(
      LogisticsService logistics,
      InventoryService inventory,
      CatalogService catalog,
      SalesService sales,
      UserDirectory users) {
    this.logistics = logistics;
    this.inventory = inventory;
    this.catalog = catalog;
    this.sales = sales;
    this.users = users;
  }

  @Override
  public ResponseEntity<WarehouseTaskPage> listWarehouseTasks(
      String assignedTo, String status, String type, Long warehouseId, Integer page, Integer size) {
    AuthenticatedUser user = CurrentUser.require();
    Long assignee = "me".equals(assignedTo) ? user.id() : null;
    Long warehouse = warehouseId;
    if (user.hasRole("warehouse") && user.warehouseId() != null) {
      warehouse = user.warehouseId();
    }
    PageResult<WarehouseTask> result =
        logistics.tasks(assignee, status, type, warehouse, page, size).map(this::toModel);
    return ResponseEntity.ok(
        new WarehouseTaskPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','warehouse')")
  public ResponseEntity<WarehouseTask> startWarehouseTask(Long id) {
    checkWarehouse(id);
    return ResponseEntity.ok(toModel(logistics.start(id, true)));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager','warehouse')")
  public ResponseEntity<WarehouseTask> completeWarehouseTask(Long id, TaskCompletionRequest body) {
    checkWarehouse(id);
    TaskCompletion completion = TaskCompletion.EMPTY;
    if (body != null) {
      Map<Long, Integer> quantities = null;
      if (body.getLines() != null && !body.getLines().isEmpty()) {
        quantities = new HashMap<>();
        for (var line : body.getLines()) {
          quantities.put(line.getProductId(), line.getQuantity());
        }
      }
      completion = new TaskCompletion(body.getCountedQty(), quantities);
    }
    return ResponseEntity.ok(toModel(logistics.complete(id, completion, true)));
  }

  private void checkWarehouse(long taskId) {
    AuthenticatedUser user = CurrentUser.require();
    if (user.hasRole("warehouse") && user.warehouseId() != null) {
      TaskDto task = logistics.task(taskId);
      if (task.warehouseId() != user.warehouseId()) {
        throw BusinessException.forbidden("La tarea es de otra bodega");
      }
    }
  }

  @Override
  public ResponseEntity<List<Carrier>> listCarriers() {
    return ResponseEntity.ok(
        logistics.carriers().stream().map(LogisticsController::toModel).toList());
  }

  @Override
  public ResponseEntity<ShipmentPage> listShipments(
      String status, Long carrierId, Integer page, Integer size) {
    PageResult<Shipment> result =
        logistics.shipments(status, carrierId, page, size).map(this::toModel);
    return ResponseEntity.ok(
        new ShipmentPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  public ResponseEntity<Resource> getShipmentLabel(Long id) {
    ShipmentDto s = logistics.shipment(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"guia-" + s.trackingNumber() + ".pdf\"")
        .body(new ByteArrayResource(logistics.label(id)));
  }

  // ---------------------------------------------------------------- mapeo

  static Carrier toModel(CarrierDto c) {
    return new Carrier(
        c.id(),
        c.code(),
        c.name(),
        c.coverageCities(),
        c.national(),
        c.onTimeRate(),
        Money.format(c.baseRate()),
        Money.format(c.ratePerKg()),
        c.active());
  }

  private WarehouseTask toModel(TaskDto t) {
    List<TaskLine> lines =
        logistics.taskLines(t).stream()
            .map(
                l -> {
                  ProductDto p = catalog.product(l.productId());
                  return new TaskLine(p.id(), p.sku(), p.name(), l.quantity());
                })
            .toList();
    return new WarehouseTask(
            t.id(),
            t.warehouseId(),
            inventory.warehouse(t.warehouseId()).code(),
            t.type(),
            t.status(),
            t.priority(),
            t.title(),
            SimTime.toOffset(t.createdAt()),
            lines)
        .detail(t.detail())
        .salesOrderId(t.salesOrderId())
        .purchaseOrderId(t.purchaseOrderId())
        .stockTransferId(t.stockTransferId())
        .referenceNumber(t.referenceNumber())
        .assignedToUserId(t.assignedToUserId())
        .assignedToName(users.nameOf(t.assignedToUserId()))
        .dueAt(SimTime.toOffset(t.dueAt()))
        .startedAt(SimTime.toOffset(t.startedAt()))
        .completedAt(SimTime.toOffset(t.completedAt()))
        .completedByName(users.nameOf(t.completedBy()));
  }

  private Shipment toModel(ShipmentDto s) {
    CarrierDto carrier = logistics.carrier(s.carrierId());
    var order = sales.order(s.salesOrderId());
    return new Shipment(
            s.id(),
            s.salesOrderId(),
            order.number(),
            carrier.id(),
            carrier.code(),
            carrier.name(),
            s.service(),
            s.trackingNumber(),
            s.status(),
            Money.format(s.cost()),
            s.weightKg().doubleValue(),
            s.originCity(),
            s.destinationCity(),
            SimTime.toOffset(s.createdAt()))
        .customerName(order.customerName())
        .eta(SimTime.toOffset(s.eta()))
        .shippedAt(SimTime.toOffset(s.shippedAt()))
        .deliveredAt(SimTime.toOffset(s.deliveredAt()));
  }
}
