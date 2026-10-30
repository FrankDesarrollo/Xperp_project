package com.xperp.logistics.service;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.CountDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockDto;
import com.xperp.inventory.api.TransferDto;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.logistics.api.CarrierCommand;
import com.xperp.logistics.api.CarrierDto;
import com.xperp.logistics.api.CarrierQuoteDto;
import com.xperp.logistics.api.CreateTask;
import com.xperp.logistics.api.LogisticsService;
import com.xperp.logistics.api.ShipmentContext;
import com.xperp.logistics.api.ShipmentDto;
import com.xperp.logistics.api.TaskCompletion;
import com.xperp.logistics.api.TaskDto;
import com.xperp.logistics.api.TaskLineDto;
import com.xperp.logistics.domain.Carrier;
import com.xperp.logistics.domain.Shipment;
import com.xperp.logistics.domain.WarehouseTask;
import com.xperp.logistics.events.SalesOrderPacked;
import com.xperp.logistics.events.ShipmentStatusChanged;
import com.xperp.logistics.events.WarehouseTaskCompleted;
import com.xperp.logistics.events.WarehouseTaskCreated;
import com.xperp.logistics.repository.CarrierRepository;
import com.xperp.logistics.repository.ShipmentRepository;
import com.xperp.logistics.repository.TaskRepository;
import com.xperp.partners.api.CarrierGateway;
import com.xperp.partners.api.PartnerUnavailableException;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.sales.api.CustomerDto;
import com.xperp.sales.api.SalesOrderDto;
import com.xperp.sales.api.SalesOrderLineDto;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.pdf.SimplePdf;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class LogisticsServiceImpl implements LogisticsService, ResettableCache {

  private static final Logger log = LoggerFactory.getLogger(LogisticsServiceImpl.class);
  private static final Set<String> OPEN = Set.of("pending", "in_progress");
  private static final Set<String> ALL = Set.of("pending", "in_progress", "done", "cancelled");
  private static final Set<String> SHIPMENT_STATUSES =
      Set.of("created", "in_transit", "delivered", "failed");

  private final CarrierRepository carriers;
  private final TaskRepository tasks;
  private final ShipmentRepository shipments;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final SalesService sales;
  private final PurchasingService purchasing;
  private final CarrierGateway carrierGateway;
  private final UserDirectory users;
  private final ApplicationEventPublisher events;
  private final SseHub sse;
  private final Clock clock;
  private final Map<Long, List<CarrierDto>> carrierCache = new ConcurrentHashMap<>();

  LogisticsServiceImpl(
      CarrierRepository carriers,
      TaskRepository tasks,
      ShipmentRepository shipments,
      CatalogService catalog,
      InventoryService inventory,
      SalesService sales,
      PurchasingService purchasing,
      CarrierGateway carrierGateway,
      UserDirectory users,
      ApplicationEventPublisher events,
      SseHub sse,
      Clock clock) {
    this.carriers = carriers;
    this.tasks = tasks;
    this.shipments = shipments;
    this.catalog = catalog;
    this.inventory = inventory;
    this.sales = sales;
    this.purchasing = purchasing;
    this.carrierGateway = carrierGateway;
    this.users = users;
    this.events = events;
    this.sse = sse;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- transportadoras

  @Override
  public CarrierDto createCarrier(CarrierCommand c) {
    Carrier carrier = new Carrier();
    carrier.setCode(c.code());
    carrier.setName(c.name());
    carrier.setPartnerCode(c.code());
    carrier.setCoverageCities(new ArrayList<>(c.coverageCities()));
    carrier.setNational(c.national());
    carrier.setBaseRate(Money.round(c.baseRate()));
    carrier.setRatePerKg(Money.round(c.ratePerKg()));
    carrier.setOnTimeRate(BigDecimal.valueOf(c.onTimeRate()));
    carriers.save(carrier);
    carrierCache.remove(TenantContext.require());
    return toDto(carrier);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CarrierDto> carriers() {
    return carrierCache.computeIfAbsent(
        TenantContext.require(),
        k -> carriers.findAllByOrderByCodeAsc().stream().map(LogisticsServiceImpl::toDto).toList());
  }

  @Override
  @Transactional(readOnly = true)
  public CarrierDto carrier(long id) {
    return carriers().stream()
        .filter(c -> c.id() == id)
        .findFirst()
        .orElseThrow(() -> BusinessException.notFound("Transportadora", id));
  }

  // ---------------------------------------------------------------- tareas

  @Override
  public TaskDto createTask(CreateTask c) {
    WarehouseTask t = new WarehouseTask();
    t.setWarehouseId(c.warehouseId());
    t.setType(c.type());
    t.setStatus("pending");
    t.setPriority(c.priority());
    t.setTitle(c.title());
    t.setDetail(c.detail());
    t.setSalesOrderId(c.salesOrderId());
    t.setPurchaseOrderId(c.purchaseOrderId());
    t.setStockTransferId(c.stockTransferId());
    t.setProductId(c.productId());
    t.setReferenceNumber(c.referenceNumber());
    t.setDueAt(c.dueAt() == null ? Instant.now(clock).plus(Duration.ofHours(2)) : c.dueAt());
    t.setAiDecisionId(c.aiDecisionId());
    users.warehouseKeeper(c.warehouseId()).ifPresent(u -> t.setAssignedToUserId(u.id()));
    tasks.save(t);
    created(t);
    return toDto(t);
  }

  @Override
  public List<TaskDto> createFulfillmentTasks(long salesOrderId, Long aiDecisionId) {
    SalesOrderDto order = sales.order(salesOrderId);
    if (!tasks.findBySalesOrderIdAndStatusIn(salesOrderId, OPEN).isEmpty()) {
      throw BusinessException.conflict("duplicate_tasks", "El pedido ya tiene tareas abiertas");
    }
    int units = order.lines().stream().mapToInt(SalesOrderLineDto::quantityFulfilled).sum();
    Random random = new Random(salesOrderId * 7919L);
    Instant now = Instant.now(clock);
    Instant pickDue = now.plus(Duration.ofMinutes(60 + random.nextInt(121)));
    Instant packDue = pickDue.plus(Duration.ofMinutes(30 + random.nextInt(61)));
    int priority =
        order.promisedAt() != null && order.promisedAt().isBefore(now.plus(Duration.ofDays(1)))
            ? 1
            : 2;

    WarehouseTask pick = newTask(order, "pick", priority, pickDue, aiDecisionId);
    pick.setTitle("Alistar " + order.number());
    pick.setDetail(
        "%d líneas, %d unidades · %s".formatted(order.lines().size(), units, order.customerName()));
    tasks.save(pick);
    WarehouseTask pack = newTask(order, "pack", priority, packDue, aiDecisionId);
    pack.setTitle("Empacar " + order.number());
    pack.setDetail("Empacar y rotular para " + order.customerCity());
    pack.setDependsOnTaskId(pick.getId());
    tasks.save(pack);
    created(pick);
    created(pack);
    return List.of(toDto(pick), toDto(pack));
  }

  private WarehouseTask newTask(
      SalesOrderDto order, String type, int priority, Instant due, Long aiDecisionId) {
    WarehouseTask t = new WarehouseTask();
    t.setWarehouseId(order.warehouseId());
    t.setType(type);
    t.setStatus("pending");
    t.setPriority(priority);
    t.setSalesOrderId(order.id());
    t.setReferenceNumber(order.number());
    t.setDueAt(due);
    t.setAiDecisionId(aiDecisionId);
    users.warehouseKeeper(order.warehouseId()).ifPresent(u -> t.setAssignedToUserId(u.id()));
    return t;
  }

  @Override
  public void cancelTask(long id) {
    WarehouseTask t = load(id);
    if (!"pending".equals(t.getStatus())) {
      throw BusinessException.conflict("not_reversible", "Solo se cancelan tareas pendientes");
    }
    t.setStatus("cancelled");
    changed(t);
  }

  @Override
  public void cancelOpenTasksForOrder(long salesOrderId) {
    for (WarehouseTask t : tasks.findBySalesOrderIdAndStatusIn(salesOrderId, OPEN)) {
      t.setStatus("cancelled");
      changed(t);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public TaskDto task(long id) {
    return toDto(load(id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<TaskLineDto> taskLines(TaskDto task) {
    try {
      return switch (task.type()) {
        case "pick", "pack" ->
            task.salesOrderId() == null
                ? List.of()
                : sales.order(task.salesOrderId()).lines().stream()
                    .filter(l -> l.quantityFulfilled() > 0)
                    .map(l -> new TaskLineDto(l.productId(), l.quantityFulfilled()))
                    .toList();
        case "receive" ->
            task.purchaseOrderId() == null
                ? List.of()
                : purchasing.order(task.purchaseOrderId()).lines().stream()
                    .map(
                        l ->
                            new TaskLineDto(
                                l.productId(),
                                l.quantityShipped() == null ? l.pending() : l.quantityShipped()))
                    .toList();
        case "transfer_out", "transfer_in" ->
            task.stockTransferId() == null
                ? List.of()
                : inventory.transfer(task.stockTransferId()).lines().stream()
                    .map(l -> new TaskLineDto(l.productId(), l.quantity()))
                    .toList();
        case "count" ->
            task.productId() == null
                ? List.of()
                : List.of(
                    new TaskLineDto(
                        task.productId(),
                        inventory.stock(task.productId(), task.warehouseId()).onHand()));
        default -> List.of();
      };
    } catch (BusinessException e) {
      return List.of();
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<TaskDto> tasks(
      Long assignedTo, String status, String type, Long warehouseId, Integer page, Integer size) {
    Set<String> statuses;
    if (status == null || status.isBlank()) {
      statuses = ALL;
    } else if ("open".equals(status)) {
      statuses = OPEN;
    } else {
      statuses = Set.of(status.split(","));
    }
    Sort sort =
        "done".equals(status)
            ? Sort.by(Sort.Direction.DESC, "completedAt")
            : Sort.by("status").descending().and(Sort.by("priority", "dueAt", "id"));
    return PageResult.of(
        tasks.search(
            statuses,
            assignedTo,
            type == null || type.isBlank() ? null : type,
            warehouseId,
            PageResult.pageable(page, size, sort)),
        this::toDto);
  }

  @Override
  public TaskDto start(long id, boolean byHuman) {
    WarehouseTask t = load(id);
    if (!"pending".equals(t.getStatus())) {
      throw BusinessException.conflict("invalid_status", "La tarea no está pendiente");
    }
    checkDependency(t);
    t.setStatus("in_progress");
    t.setStartedAt(Instant.now(clock));
    if (byHuman) {
      // Si una persona abre la tarea, el bodeguero virtual la deja de lado (AGENTS.md, 10.2).
      t.setHandledByHuman(true);
      Long userId = CurrentUser.idOrNull();
      if (userId != null) {
        t.setAssignedToUserId(userId);
      }
    }
    if ("pick".equals(t.getType()) && t.getSalesOrderId() != null) {
      sales.markPicking(t.getSalesOrderId());
    }
    changed(t);
    return toDto(t);
  }

  @Override
  public TaskDto complete(long id, TaskCompletion completion, boolean byHuman) {
    WarehouseTask t = load(id);
    if ("pending".equals(t.getStatus())) {
      start(id, byHuman);
    } else if (!"in_progress".equals(t.getStatus())) {
      throw BusinessException.conflict("invalid_status", "La tarea ya está cerrada");
    }
    TaskCompletion result = completion == null ? TaskCompletion.EMPTY : completion;
    String who =
        byHuman
            ? CurrentUser.nameOr("Bodega")
            : Optional.ofNullable(users.nameOf(t.getAssignedToUserId())).orElse("Bodega")
                + " (virtual)";
    switch (t.getType()) {
      case "pick" -> {
        if (t.getSalesOrderId() != null) {
          sales.markPicking(t.getSalesOrderId());
        }
      }
      case "pack" -> {
        if (t.getSalesOrderId() != null) {
          sales.markPacked(t.getSalesOrderId());
          events.publishEvent(
              new SalesOrderPacked(
                  TenantContext.require(), t.getSalesOrderId(), t.getWarehouseId()));
        }
      }
      case "receive" -> {
        if (t.getPurchaseOrderId() != null) {
          purchasing.receive(t.getPurchaseOrderId(), result.quantities(), who);
        }
      }
      case "count" -> completeCount(t, result);
      case "transfer_out" -> {
        TransferDto transfer = inventory.shipTransfer(t.getStockTransferId());
        WarehouseDto origin = inventory.warehouse(transfer.fromWarehouseId());
        createTask(
            new CreateTask(
                transfer.toWarehouseId(),
                "transfer_in",
                2,
                "Recibir traslado " + transfer.number(),
                "Llega desde " + origin.code() + " con Envíos Cordillera (TRP-02)",
                null,
                null,
                transfer.id(),
                null,
                transfer.number(),
                Instant.now(clock).plus(Duration.ofDays(2)),
                t.getAiDecisionId()));
      }
      case "transfer_in" -> inventory.receiveTransfer(t.getStockTransferId());
      default -> log.warn("Tipo de tarea desconocido: {}", t.getType());
    }
    t.setStatus("done");
    t.setCompletedAt(Instant.now(clock));
    t.setCompletedBy(byHuman ? CurrentUser.idOrNull() : t.getAssignedToUserId());
    changed(t);
    events.publishEvent(
        new WarehouseTaskCompleted(
            TenantContext.require(),
            t.getId(),
            t.getType(),
            t.getWarehouseId(),
            t.getSalesOrderId(),
            t.getPurchaseOrderId(),
            t.getStockTransferId(),
            byHuman));
    return toDto(t);
  }

  private void completeCount(WarehouseTask t, TaskCompletion result) {
    if (t.getProductId() == null) {
      return;
    }
    Integer counted = result.countedQty();
    if (counted == null) {
      // Bodeguero virtual: el reconteo confirma la diferencia del conteo original.
      StockDto stock = inventory.stock(t.getProductId(), t.getWarehouseId());
      int difference = 0;
      if (t.getReferenceNumber() != null && t.getReferenceNumber().startsWith("CNT-")) {
        CountDto original = inventory.count(Long.parseLong(t.getReferenceNumber().substring(4)));
        difference = original.difference();
      }
      counted = Math.max(0, stock.onHand() + difference);
    }
    inventory.recordCount(t.getProductId(), t.getWarehouseId(), counted, true);
  }

  private void checkDependency(WarehouseTask t) {
    if (t.getDependsOnTaskId() != null) {
      WarehouseTask dep = load(t.getDependsOnTaskId());
      if (!"done".equals(dep.getStatus()) && !"cancelled".equals(dep.getStatus())) {
        throw BusinessException.conflict(
            "dependency_pending", "Primero hay que terminar: " + dep.getTitle());
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<TaskDto> dueForVirtualWorker(Instant now, int limit) {
    List<TaskDto> due = new ArrayList<>();
    for (WarehouseTask t : tasks.findDue(now, PageRequest.of(0, limit))) {
      if (t.getDependsOnTaskId() != null) {
        WarehouseTask dep = tasks.findById(t.getDependsOnTaskId()).orElse(null);
        if (dep != null && OPEN.contains(dep.getStatus())) {
          continue;
        }
      }
      due.add(toDto(t));
    }
    return due;
  }

  // ---------------------------------------------------------------- envíos

  @Override
  @Transactional(readOnly = true)
  public ShipmentContext shipmentContext(long salesOrderId) {
    SalesOrderDto order = sales.order(salesOrderId);
    CustomerDto customer = sales.customer(order.customerId());
    WarehouseDto warehouse = inventory.warehouse(order.warehouseId());
    Map<Long, ProductDto> products =
        catalog.productsById(order.lines().stream().map(SalesOrderLineDto::productId).toList());
    BigDecimal weight = BigDecimal.ZERO;
    for (SalesOrderLineDto line : order.lines()) {
      weight =
          weight.add(
              products
                  .get(line.productId())
                  .weightKg()
                  .multiply(BigDecimal.valueOf(line.quantityFulfilled())));
    }
    double kg = Math.max(0.5, weight.setScale(2, RoundingMode.HALF_UP).doubleValue());
    return new ShipmentContext(order.id(), order.number(), warehouse.city(), customer.city(), kg);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CarrierQuoteDto> quote(long salesOrderId) {
    ShipmentContext ctx = shipmentContext(salesOrderId);
    List<CarrierQuoteDto> quotes = new ArrayList<>();
    for (CarrierDto carrier : carriers()) {
      if (!carrier.active() || !carrier.covers(ctx.destinationCity())) {
        continue;
      }
      try {
        carrierGateway
            .quote(carrier.code(), ctx.originCity(), ctx.destinationCity(), ctx.weightKg())
            .ifPresent(
                q -> quotes.add(new CarrierQuoteDto(carrier, q.service(), q.cost(), q.etaDays())));
      } catch (PartnerUnavailableException e) {
        log.warn("{}", e.getMessage());
      }
    }
    return quotes;
  }

  @Override
  public ShipmentDto createShipment(
      long salesOrderId,
      long carrierId,
      String service,
      BigDecimal cost,
      int etaDays,
      Long aiDecisionId) {
    if (shipments.existsBySalesOrderId(salesOrderId)) {
      throw BusinessException.conflict("duplicate_shipment", "El pedido ya tiene envío");
    }
    SalesOrderDto order = sales.order(salesOrderId);
    if (!"packed".equals(order.status())) {
      throw BusinessException.conflict("invalid_status", "El pedido todavía no está empacado");
    }
    CarrierDto carrier = carrier(carrierId);
    ShipmentContext ctx = shipmentContext(salesOrderId);
    String tracking =
        carrierGateway.createShipment(
            carrier.code(),
            order.number(),
            ctx.originCity(),
            ctx.destinationCity(),
            ctx.weightKg(),
            service);
    Instant now = Instant.now(clock);
    Shipment s = new Shipment();
    s.setSalesOrderId(salesOrderId);
    s.setCarrierId(carrierId);
    s.setService(service);
    s.setTrackingNumber(tracking);
    s.setStatus("created");
    s.setCost(Money.round(cost));
    s.setWeightKg(BigDecimal.valueOf(ctx.weightKg()));
    s.setOriginCity(ctx.originCity());
    s.setDestinationCity(ctx.destinationCity());
    s.setEta(now.plus(Duration.ofDays(etaDays)));
    s.setShippedAt(now);
    s.setAiDecisionId(aiDecisionId);
    shipments.save(s);
    s.setLabelPath("/api/v1/shipments/" + s.getId() + "/label");
    for (SalesOrderLineDto line : order.lines()) {
      inventory.dispatch(
          line.productId(),
          order.warehouseId(),
          line.quantityFulfilled(),
          "sales_order",
          order.id());
    }
    sales.markShipped(salesOrderId);
    shipmentChanged(s);
    return toDto(s);
  }

  /** Aplica el estado que reportó la transportadora. Lo llama el listener del webhook. */
  void applyCarrierUpdate(String trackingNumber, String status, Instant occurredAt) {
    Optional<Shipment> found = shipments.findByTrackingNumber(trackingNumber);
    if (found.isEmpty()) {
      log.warn("Webhook para una guía desconocida: {}", trackingNumber);
      return;
    }
    Shipment s = found.get();
    if ("delivered".equals(s.getStatus())) {
      return;
    }
    s.setStatus(status);
    if ("delivered".equals(status)) {
      s.setDeliveredAt(Instant.now(clock));
      sales.markDelivered(s.getSalesOrderId());
    }
    shipmentChanged(s);
    events.publishEvent(
        new ShipmentStatusChanged(
            TenantContext.require(),
            s.getId(),
            s.getSalesOrderId(),
            s.getTrackingNumber(),
            status));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasShipment(long salesOrderId) {
    return shipments.existsBySalesOrderId(salesOrderId);
  }

  @Override
  @Transactional(readOnly = true)
  public ShipmentDto shipment(long id) {
    return toDto(shipments.findById(id).orElseThrow(() -> BusinessException.notFound("Envío", id)));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<ShipmentDto> shipments(
      String status, Long carrierId, Integer page, Integer size) {
    Set<String> statuses =
        status == null || status.isBlank() ? SHIPMENT_STATUSES : Set.of(status.split(","));
    return PageResult.of(
        shipments.search(
            statuses,
            carrierId,
            PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"))),
        LogisticsServiceImpl::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] label(long id) {
    ShipmentDto s = shipment(id);
    SalesOrderDto order = sales.order(s.salesOrderId());
    CustomerDto customer = sales.customer(order.customerId());
    CarrierDto carrier = carrier(s.carrierId());
    SimplePdf pdf =
        SimplePdf.of("Guía de despacho " + s.trackingNumber(), carrier.name() + " · " + s.service())
            .field("Pedido", order.number())
            .field("Remitente", "Distribuidora La Ceiba S.A.S. · " + s.originCity())
            .field("Destinatario", customer.name())
            .field(
                "Dirección",
                (customer.address() == null ? "" : customer.address() + ", ") + customer.city())
            .field("Peso", s.weightKg().setScale(2, RoundingMode.HALF_UP) + " kg")
            .field("Valor del flete", Money.display(s.cost()))
            .field("Despachado", SimTime.display(s.shippedAt()))
            .field("Entrega estimada", SimTime.display(s.eta()))
            .table(new String[] {"SKU", "Producto", "Unidades"}, new float[] {1.2f, 5f, 1.4f});
    Map<Long, ProductDto> products =
        catalog.productsById(order.lines().stream().map(SalesOrderLineDto::productId).toList());
    for (SalesOrderLineDto line : order.lines()) {
      if (line.quantityFulfilled() > 0) {
        ProductDto p = products.get(line.productId());
        pdf.row(p.sku(), p.name(), String.valueOf(line.quantityFulfilled()));
      }
    }
    pdf.note("Guía generada por Xperp con datos demo. Las empresas son ficticias.");
    return pdf.render();
  }

  // ---------------------------------------------------------------- utilidades

  private WarehouseTask load(long id) {
    return tasks.findById(id).orElseThrow(() -> BusinessException.notFound("Tarea", id));
  }

  private void created(WarehouseTask t) {
    events.publishEvent(
        new WarehouseTaskCreated(
            TenantContext.require(),
            t.getId(),
            t.getType(),
            t.getWarehouseId(),
            t.getTitle(),
            t.getAssignedToUserId()));
    sse.publish("task.created", taskData(t), SseHub.Audience.ALL, "t" + t.getId());
  }

  private void changed(WarehouseTask t) {
    sse.publish("task.changed", taskData(t), SseHub.Audience.ALL, "t" + t.getId());
  }

  private static Map<String, Object> taskData(WarehouseTask t) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", t.getId());
    data.put("type", t.getType());
    data.put("status", t.getStatus());
    data.put("warehouse_id", t.getWarehouseId());
    data.put("title", t.getTitle());
    data.put("assigned_to_user_id", t.getAssignedToUserId());
    return data;
  }

  private void shipmentChanged(Shipment s) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", s.getId());
    data.put("sales_order_id", s.getSalesOrderId());
    data.put("tracking_number", s.getTrackingNumber());
    data.put("status", s.getStatus());
    sse.publish("shipment.changed", data, SseHub.Audience.ALL, "s" + s.getId());
  }

  @Override
  public void clearCache() {
    carrierCache.clear();
  }

  private static CarrierDto toDto(Carrier c) {
    return new CarrierDto(
        c.getId(),
        c.getCode(),
        c.getName(),
        List.copyOf(c.getCoverageCities()),
        c.isNational(),
        c.getBaseRate(),
        c.getRatePerKg(),
        c.getOnTimeRate().doubleValue(),
        c.isActive());
  }

  private TaskDto toDto(WarehouseTask t) {
    return new TaskDto(
        t.getId(),
        t.getWarehouseId(),
        t.getType(),
        t.getStatus(),
        t.getPriority(),
        t.getTitle(),
        t.getDetail(),
        t.getSalesOrderId(),
        t.getPurchaseOrderId(),
        t.getStockTransferId(),
        t.getProductId(),
        t.getReferenceNumber(),
        t.getDependsOnTaskId(),
        t.getAssignedToUserId(),
        t.isHandledByHuman(),
        t.getDueAt(),
        t.getStartedAt(),
        t.getCompletedAt(),
        t.getCompletedBy(),
        t.getAiDecisionId(),
        t.getCreatedAt());
  }

  private static ShipmentDto toDto(Shipment s) {
    return new ShipmentDto(
        s.getId(),
        s.getSalesOrderId(),
        s.getCarrierId(),
        s.getService(),
        s.getTrackingNumber(),
        s.getStatus(),
        s.getCost(),
        s.getWeightKg(),
        s.getOriginCity(),
        s.getDestinationCity(),
        s.getEta(),
        s.getShippedAt(),
        s.getDeliveredAt(),
        s.getAiDecisionId(),
        s.getCreatedAt());
  }
}
