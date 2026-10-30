package com.xperp.purchasing.service;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.partners.api.PartnerUnavailableException;
import com.xperp.partners.api.SupplierGateway;
import com.xperp.purchasing.api.CreatePurchaseOrder;
import com.xperp.purchasing.api.OfferDto;
import com.xperp.purchasing.api.OpenLine;
import com.xperp.purchasing.api.PurchaseLine;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierCommand;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.purchasing.domain.GoodsReceipt;
import com.xperp.purchasing.domain.GoodsReceiptItem;
import com.xperp.purchasing.domain.PurchaseOrder;
import com.xperp.purchasing.domain.PurchaseOrderEvent;
import com.xperp.purchasing.domain.PurchaseOrderItem;
import com.xperp.purchasing.domain.Supplier;
import com.xperp.purchasing.domain.SupplierPriceHistory;
import com.xperp.purchasing.domain.SupplierProduct;
import com.xperp.purchasing.domain.SupplierProductId;
import com.xperp.purchasing.domain.SupplierWarehouse;
import com.xperp.purchasing.domain.SupplierWarehouseId;
import com.xperp.purchasing.events.GoodsReceived;
import com.xperp.purchasing.events.PurchaseOrderSent;
import com.xperp.purchasing.events.PurchaseOrderStatusChanged;
import com.xperp.purchasing.events.SupplierPriceChanged;
import com.xperp.purchasing.repository.GoodsReceiptRepository;
import com.xperp.purchasing.repository.PriceHistoryRepository;
import com.xperp.purchasing.repository.PurchaseOrderRepository;
import com.xperp.purchasing.repository.SupplierProductRepository;
import com.xperp.purchasing.repository.SupplierRepository;
import com.xperp.purchasing.repository.SupplierWarehouseRepository;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.pdf.SimplePdf;
import com.xperp.shared.persistence.DocumentNumbers;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PurchasingServiceImpl implements PurchasingService, ResettableCache {

  private static final Logger log = LoggerFactory.getLogger(PurchasingServiceImpl.class);

  static final Set<String> OPEN_STATUSES =
      Set.of("draft", "pending_approval", "sent", "confirmed", "delayed", "shipped");
  static final Set<String> ALL_STATUSES =
      Set.of(
          "draft",
          "pending_approval",
          "sent",
          "confirmed",
          "delayed",
          "rejected",
          "shipped",
          "received",
          "cancelled");
  private static final Set<String> CANCELLABLE =
      Set.of("draft", "pending_approval", "sent", "confirmed", "delayed", "rejected");

  private final SupplierRepository suppliers;
  private final SupplierWarehouseRepository leadTimes;
  private final SupplierProductRepository offers;
  private final PriceHistoryRepository priceHistory;
  private final PurchaseOrderRepository orders;
  private final GoodsReceiptRepository receipts;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final SupplierGateway gateway;
  private final DocumentNumbers numbers;
  private final UserDirectory users;
  private final ApplicationEventPublisher events;
  private final SseHub sse;
  private final Clock clock;
  private final Map<Long, Map<Long, SupplierDto>> supplierCache = new ConcurrentHashMap<>();

  PurchasingServiceImpl(
      SupplierRepository suppliers,
      SupplierWarehouseRepository leadTimes,
      SupplierProductRepository offers,
      PriceHistoryRepository priceHistory,
      PurchaseOrderRepository orders,
      GoodsReceiptRepository receipts,
      CatalogService catalog,
      InventoryService inventory,
      SupplierGateway gateway,
      DocumentNumbers numbers,
      UserDirectory users,
      ApplicationEventPublisher events,
      SseHub sse,
      Clock clock) {
    this.suppliers = suppliers;
    this.leadTimes = leadTimes;
    this.offers = offers;
    this.priceHistory = priceHistory;
    this.orders = orders;
    this.receipts = receipts;
    this.catalog = catalog;
    this.inventory = inventory;
    this.gateway = gateway;
    this.numbers = numbers;
    this.users = users;
    this.events = events;
    this.sse = sse;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- proveedores

  @Override
  public SupplierDto createSupplier(SupplierCommand c) {
    Supplier s = new Supplier();
    s.setCode(c.code());
    s.setName(c.name());
    s.setNit(c.nit());
    s.setCity(c.city());
    s.setEmail(c.email());
    s.setPartnerCode(c.code());
    s.setMinOrderAmount(Money.round(c.minOrderAmount()));
    s.setPaymentTermsDays(c.paymentTermsDays());
    s.setOnTimeRate(BigDecimal.valueOf(c.onTimeRate()));
    s.setFillRate(BigDecimal.valueOf(c.fillRate()));
    suppliers.save(s);
    evict();
    return supplier(s.getId());
  }

  @Override
  public void setLeadTime(long supplierId, long warehouseId, int days) {
    SupplierWarehouseId id = new SupplierWarehouseId(supplierId, warehouseId);
    SupplierWarehouse sw = leadTimes.findById(id).orElseGet(SupplierWarehouse::new);
    sw.setId(id);
    sw.setLeadTimeDays(days);
    leadTimes.save(sw);
    evict();
  }

  @Override
  public void upsertOffer(
      long supplierId, long productId, BigDecimal unitCost, int minOrderCases, boolean preferred) {
    SupplierProductId id = new SupplierProductId(supplierId, productId);
    SupplierProduct offer = offers.findById(id).orElseGet(SupplierProduct::new);
    offer.setId(id);
    offer.setUnitCost(Money.round(unitCost));
    offer.setMinOrderCases(minOrderCases);
    offer.setPreferred(preferred);
    offer.setPriceUpdatedAt(Instant.now(clock));
    offers.save(offer);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SupplierDto> suppliers() {
    return supplierMap().values().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public SupplierDto supplier(long id) {
    SupplierDto dto = supplierMap().get(id);
    if (dto == null) {
      throw BusinessException.notFound("Proveedor", id);
    }
    return dto;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SupplierDto> supplierByCode(String code) {
    return supplierMap().values().stream().filter(s -> s.code().equals(code)).findFirst();
  }

  private Map<Long, SupplierDto> supplierMap() {
    return supplierCache.computeIfAbsent(
        TenantContext.require(),
        k -> {
          Map<Long, Map<Long, Integer>> times = new HashMap<>();
          leadTimes
              .findAll()
              .forEach(
                  sw ->
                      times
                          .computeIfAbsent(sw.getId().supplierId(), x -> new HashMap<>())
                          .put(sw.getId().warehouseId(), sw.getLeadTimeDays()));
          Map<Long, SupplierDto> map = new ConcurrentHashMap<>();
          for (Supplier s : suppliers.findAllByOrderByCodeAsc()) {
            map.put(s.getId(), toDto(s, times.getOrDefault(s.getId(), Map.of())));
          }
          return map;
        });
  }

  @Override
  @Transactional(readOnly = true)
  public List<OfferDto> offersForProduct(long productId) {
    return offers.findByIdProductId(productId).stream()
        .map(this::toDto)
        .filter(o -> o.supplier().active())
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<OfferDto> offersForSupplier(long supplierId) {
    supplier(supplierId);
    return offers.findByIdSupplierId(supplierId).stream().map(this::toDto).toList();
  }

  @Override
  public int syncSupplierPrices() {
    int changes = 0;
    for (SupplierDto s : suppliers()) {
      List<SupplierGateway.CatalogPrice> prices;
      try {
        prices = gateway.catalog(s.code());
      } catch (PartnerUnavailableException e) {
        log.warn("{}", e.getMessage());
        continue;
      }
      for (SupplierGateway.CatalogPrice price : prices) {
        Optional<ProductDto> product = catalog.productBySku(price.sku());
        if (product.isEmpty()) {
          continue;
        }
        var id = new SupplierProductId(s.id(), product.get().id());
        Optional<SupplierProduct> offer = offers.findById(id);
        if (offer.isEmpty()) {
          continue;
        }
        BigDecimal previous = offer.get().getUnitCost();
        BigDecimal next = Money.round(price.unitCost());
        if (previous.compareTo(next) == 0) {
          continue;
        }
        offer.get().setUnitCost(next);
        offer.get().setPriceUpdatedAt(price.validFrom());
        SupplierPriceHistory history = new SupplierPriceHistory();
        history.setSupplierId(s.id());
        history.setProductId(product.get().id());
        history.setUnitCost(next);
        history.setValidFrom(price.validFrom());
        priceHistory.save(history);
        double pct =
            next.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP)
                .doubleValue();
        events.publishEvent(
            new SupplierPriceChanged(
                TenantContext.require(), s.id(), product.get().id(), previous, next, pct));
        changes++;
      }
    }
    return changes;
  }

  // ---------------------------------------------------------------- órdenes de compra

  @Override
  public PurchaseOrderDto create(CreatePurchaseOrder c) {
    if (c.idempotencyKey() != null) {
      Optional<PurchaseOrder> existing = orders.findByIdempotencyKey(c.idempotencyKey());
      if (existing.isPresent()) {
        throw BusinessException.conflict(
            "duplicate_purchase_order",
            "Ya existe la OC %s para este disparador".formatted(existing.get().getNumber()));
      }
    }
    Supplier supplier =
        suppliers
            .findById(c.supplierId())
            .orElseThrow(() -> BusinessException.notFound("Proveedor", c.supplierId()));
    if (!supplier.isActive()) {
      throw BusinessException.conflict("inactive_supplier", "El proveedor está inactivo");
    }
    WarehouseDto warehouse = inventory.warehouse(c.warehouseId());
    if (supplier(supplier.getId()).leadTimeTo(warehouse.id()) == null) {
      throw BusinessException.invalid(
          "warehouse_not_served",
          "%s no despacha a %s".formatted(supplier.getName(), warehouse.code()));
    }
    if (c.lines() == null || c.lines().isEmpty()) {
      throw BusinessException.invalid("empty_order", "La OC no tiene líneas");
    }

    PurchaseOrder po = new PurchaseOrder();
    po.setNumber(numbers.next(DocumentNumbers.PURCHASE_ORDER));
    po.setSupplier(supplier);
    po.setWarehouseId(warehouse.id());
    po.setStatus(c.pendingApproval() ? "pending_approval" : "draft");
    po.setCreatedByUserId(c.createdByUserId());
    po.setAiDecisionId(c.aiDecisionId());
    po.setReplacesPoId(c.replacesPoId());
    po.setIdempotencyKey(c.idempotencyKey());

    Map<Long, PurchaseLine> merged = new LinkedHashMap<>();
    for (PurchaseLine line : c.lines()) {
      if (line.quantity() <= 0) {
        throw BusinessException.invalid("invalid_quantity", "Las cantidades deben ser positivas");
      }
      merged.merge(
          line.productId(),
          line,
          (a, b) -> new PurchaseLine(a.productId(), a.quantity() + b.quantity(), a.unitCost()));
    }
    for (PurchaseLine line : merged.values()) {
      SupplierProduct offer =
          offers
              .findById(new SupplierProductId(supplier.getId(), line.productId()))
              .orElseThrow(
                  () ->
                      BusinessException.invalid(
                          "product_not_offered",
                          "%s no vende %s"
                              .formatted(
                                  supplier.getName(), catalog.product(line.productId()).sku())));
      PurchaseOrderItem item = new PurchaseOrderItem();
      item.setOrder(po);
      item.setProductId(line.productId());
      item.setQuantity(line.quantity());
      item.setUnitCost(
          line.unitCost() == null ? offer.getUnitCost() : Money.round(line.unitCost()));
      po.getItems().add(item);
    }
    recalc(po);
    addEvent(po, po.getStatus(), c.note() == null ? "OC creada" : c.note());
    orders.save(po);
    changed(po);
    return toDto(po);
  }

  @Override
  public PurchaseOrderDto send(long id) {
    PurchaseOrder po = lock(id);
    if (!Set.of("draft", "pending_approval").contains(po.getStatus())) {
      throw BusinessException.conflict(
          "invalid_status", "Solo se envían OC en borrador o aprobadas");
    }
    String previous = po.getStatus();
    po.setStatus("sent");
    po.setSentAt(Instant.now(clock));
    po.setStatusReason(null);
    deliver(po);
    addEvent(po, "sent", "Enviada a " + po.getSupplier().getName());
    publishSent(po);
    publishStatus(po, previous, null);
    changed(po);
    return toDto(po);
  }

  /** Llama a partners-mock. Si no responde, la OC queda enviada y se reintenta luego. */
  private void deliver(PurchaseOrder po) {
    try {
      String externalId =
          gateway.sendOrder(
              po.getSupplier().getPartnerCode(),
              po.getNumber(),
              inventory.warehouse(po.getWarehouseId()).code(),
              po.getItems().stream()
                  .map(
                      i ->
                          new SupplierGateway.OrderLine(
                              catalog.product(i.getProductId()).sku(),
                              i.getQuantity(),
                              i.getUnitCost()))
                  .toList());
      po.setExternalId(externalId);
    } catch (PartnerUnavailableException e) {
      log.warn("{}: {}", po.getNumber(), e.getMessage());
      po.setStatusReason("El proveedor no respondió; se reintentará el envío");
    }
  }

  @Override
  public int retryUnsent() {
    int count = 0;
    for (PurchaseOrder po : orders.findByStatusAndExternalIdIsNull("sent")) {
      deliver(po);
      if (po.getExternalId() != null) {
        po.setStatusReason(null);
        addEvent(po, "sent", "Reenviada al proveedor");
        count++;
      }
    }
    return count;
  }

  @Override
  public PurchaseOrderDto cancel(long id, String reason) {
    PurchaseOrder po = lock(id);
    if (!CANCELLABLE.contains(po.getStatus())) {
      throw BusinessException.conflict(
          "not_reversible", "La OC ya fue despachada o cerrada y no se puede cancelar");
    }
    String previous = po.getStatus();
    po.setStatus("cancelled");
    po.setStatusReason(truncate(reason == null ? "Cancelada" : reason));
    addEvent(po, "cancelled", po.getStatusReason());
    publishStatus(po, previous, po.getStatusReason());
    changed(po);
    return toDto(po);
  }

  @Override
  public PurchaseOrderDto reassign(long id, long newSupplierId, String reason, Long aiDecisionId) {
    PurchaseOrder old = lock(id);
    if (!Set.of("sent", "confirmed", "delayed", "rejected").contains(old.getStatus())) {
      throw BusinessException.conflict(
          "invalid_status", "Solo se reasignan OC enviadas, confirmadas, retrasadas o rechazadas");
    }
    if (old.getSupplier().getId() == newSupplierId) {
      throw BusinessException.invalid("same_supplier", "El proveedor nuevo es el mismo");
    }
    List<PurchaseLine> lines = new ArrayList<>();
    for (PurchaseOrderItem item : old.getItems()) {
      int pending = item.getQuantity() - item.getQuantityReceived();
      if (pending > 0) {
        lines.add(new PurchaseLine(item.getProductId(), pending, null));
      }
    }
    PurchaseOrderDto created =
        create(
            new CreatePurchaseOrder(
                newSupplierId,
                old.getWarehouseId(),
                lines,
                null,
                aiDecisionId,
                old.getId(),
                null,
                false,
                "Reemplaza a " + old.getNumber()));
    String previous = old.getStatus();
    if (!"rejected".equals(previous)) {
      old.setStatus("cancelled");
    }
    old.setStatusReason(truncate("Reasignada a " + created.number() + ". " + reason));
    addEvent(old, old.getStatus(), "Reasignada a " + created.number());
    publishStatus(old, previous, old.getStatusReason());
    changed(old);
    return send(created.id());
  }

  @Override
  @Transactional(readOnly = true)
  public PurchaseOrderDto order(long id) {
    return toDto(orders.findById(id).orElseThrow(() -> BusinessException.notFound("OC", id)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PurchaseOrderDto> byNumber(String number) {
    return orders.findByNumber(number).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PurchaseOrderDto> byIdempotencyKey(String key) {
    return orders.findByIdempotencyKey(key).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<PurchaseOrderDto> orders(
      String status, Long supplierId, Long warehouseId, Integer page, Integer size) {
    Set<String> statuses;
    if (status == null || status.isBlank()) {
      statuses = ALL_STATUSES;
    } else if ("open".equals(status)) {
      statuses = OPEN_STATUSES;
    } else {
      statuses = Set.of(status.split(","));
    }
    return PageResult.of(
        orders.search(
            statuses,
            supplierId,
            warehouseId,
            PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"))),
        this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public List<OpenLine> openLines() {
    List<OpenLine> lines = new ArrayList<>();
    for (PurchaseOrder po : orders.findOpenWithItems(OPEN_STATUSES)) {
      for (PurchaseOrderItem item : po.getItems()) {
        int pending = item.getQuantity() - item.getQuantityReceived();
        if (pending > 0) {
          lines.add(
              new OpenLine(
                  po.getId(),
                  po.getNumber(),
                  po.getSupplier().getId(),
                  po.getWarehouseId(),
                  item.getProductId(),
                  pending,
                  po.getStatus(),
                  po.getExpectedAt()));
        }
      }
    }
    return lines;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasOpenOrder(long productId, long warehouseId) {
    return openLines().stream()
        .anyMatch(l -> l.productId() == productId && l.warehouseId() == warehouseId);
  }

  @Override
  public PurchaseOrderDto receive(long id, Map<Long, Integer> quantities, String receivedBy) {
    PurchaseOrder po = lock(id);
    if (!Set.of("sent", "confirmed", "delayed", "shipped").contains(po.getStatus())) {
      throw BusinessException.conflict("invalid_status", "La OC no está pendiente de recibir");
    }
    Instant now = Instant.now(clock);
    GoodsReceipt receipt = new GoodsReceipt();
    receipt.setPurchaseOrderId(po.getId());
    receipt.setReceivedBy(receivedBy);
    receipt.setReceivedAt(now);
    Map<Long, BigDecimal> byCategory = new HashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (PurchaseOrderItem item : po.getItems()) {
      int expected =
          item.getQuantityShipped() == null ? item.getQuantity() : item.getQuantityShipped();
      int qty =
          quantities == null ? expected : quantities.getOrDefault(item.getProductId(), expected);
      qty = Math.max(0, Math.min(qty, item.getQuantity() - item.getQuantityReceived()));
      if (qty == 0) {
        continue;
      }
      item.setQuantityReceived(item.getQuantityReceived() + qty);
      GoodsReceiptItem ri = new GoodsReceiptItem();
      ri.setReceipt(receipt);
      ri.setProductId(item.getProductId());
      ri.setQuantityReceived(qty);
      receipt.getItems().add(ri);
      inventory.receive(
          item.getProductId(),
          po.getWarehouseId(),
          qty,
          item.getUnitCost(),
          "purchase_order",
          po.getId(),
          receivedBy);
      BigDecimal amount = item.getUnitCost().multiply(BigDecimal.valueOf(qty));
      total = total.add(amount);
      byCategory.merge(catalog.product(item.getProductId()).categoryId(), amount, BigDecimal::add);
    }
    receipts.save(receipt);
    String previous = po.getStatus();
    po.setStatus("received");
    po.setReceivedAt(now);
    boolean partial =
        po.getItems().stream().anyMatch(i -> i.getQuantityReceived() < i.getQuantity());
    addEvent(
        po,
        "received",
        partial ? "Recibida parcial: " + Money.display(total) : "Recibida completa");
    events.publishEvent(
        new GoodsReceived(
            TenantContext.require(),
            po.getId(),
            po.getNumber(),
            po.getSupplier().getId(),
            po.getSupplier().getPaymentTermsDays(),
            Money.round(total),
            byCategory));
    publishStatus(po, previous, partial ? "Entrega parcial" : null);
    changed(po);
    return toDto(po);
  }

  @Override
  public void linkDecision(long purchaseOrderId, long decisionId) {
    orders.findById(purchaseOrderId).ifPresent(po -> po.setAiDecisionId(decisionId));
  }

  /** Aplica la respuesta del proveedor que llegó por webhook. Lo llama el listener. */
  void applySupplierUpdate(
      String poNumber,
      String externalId,
      String status,
      Instant expectedAt,
      String reason,
      Map<String, Integer> shippedBySku) {
    Optional<PurchaseOrder> found = orders.findByNumber(poNumber);
    if (found.isEmpty()) {
      log.warn("Webhook para una OC desconocida: {}", poNumber);
      return;
    }
    PurchaseOrder po = lock(found.get().getId());
    if (Set.of("cancelled", "received", "rejected").contains(po.getStatus())) {
      log.debug("Se ignora {} para {} ({})", status, poNumber, po.getStatus());
      return;
    }
    if (po.getExternalId() == null && externalId != null) {
      po.setExternalId(externalId);
    }
    String previous = po.getStatus();
    Instant now = Instant.now(clock);
    switch (status) {
      case "confirmed" -> {
        po.setStatus("confirmed");
        po.setConfirmedAt(now);
        po.setExpectedAt(expectedAt);
        if (po.getFirstExpectedAt() == null) {
          po.setFirstExpectedAt(expectedAt);
        }
        addEvent(po, "confirmed", "Entrega estimada " + SimTime.display(expectedAt));
      }
      case "rejected" -> {
        po.setStatus("rejected");
        po.setStatusReason(truncate(reason == null ? "Rechazada por el proveedor" : reason));
        addEvent(po, "rejected", po.getStatusReason());
      }
      case "delayed" -> {
        po.setStatus("delayed");
        if (po.getFirstExpectedAt() == null) {
          po.setFirstExpectedAt(po.getExpectedAt());
        }
        po.setExpectedAt(expectedAt);
        po.setStatusReason(truncate(reason == null ? "Retraso del proveedor" : reason));
        addEvent(
            po,
            "delayed",
            "Nueva fecha " + SimTime.display(expectedAt) + ". " + po.getStatusReason());
      }
      case "shipped" -> {
        po.setStatus("shipped");
        applyShipped(po, shippedBySku);
        addEvent(po, "shipped", "Despachada por el proveedor");
      }
      case "delivered" -> {
        if (!"shipped".equals(po.getStatus())) {
          po.setStatus("shipped");
        }
        applyShipped(po, shippedBySku);
        addEvent(po, "delivered", "Entregada en bodega; pendiente de recepción");
        events.publishEvent(
            new com.xperp.purchasing.events.PurchaseOrderDelivered(
                TenantContext.require(), po.getId(), po.getNumber(), po.getWarehouseId()));
      }
      default -> log.warn("Estado de proveedor desconocido: {}", status);
    }
    if (!previous.equals(po.getStatus())) {
      publishStatus(po, previous, po.getStatusReason());
    }
    changed(po);
  }

  private void applyShipped(PurchaseOrder po, Map<String, Integer> shippedBySku) {
    if (shippedBySku == null || shippedBySku.isEmpty()) {
      return;
    }
    for (PurchaseOrderItem item : po.getItems()) {
      Integer qty = shippedBySku.get(catalog.product(item.getProductId()).sku());
      if (qty != null) {
        item.setQuantityShipped(Math.min(qty, item.getQuantity()));
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] pdf(long id) {
    PurchaseOrder po = orders.findById(id).orElseThrow(() -> BusinessException.notFound("OC", id));
    WarehouseDto wh = inventory.warehouse(po.getWarehouseId());
    SimplePdf pdf =
        SimplePdf.of(
                "Orden de compra " + po.getNumber(),
                "Distribuidora La Ceiba S.A.S. · NIT 999999999-1")
            .field(
                "Proveedor", po.getSupplier().getName() + " (" + po.getSupplier().getCode() + ")")
            .field("NIT proveedor", po.getSupplier().getNit())
            .field("Entregar en", wh.name() + " · " + wh.city())
            .field(
                "Fecha",
                SimTime.display(po.getSentAt() == null ? po.getCreatedAt() : po.getSentAt()))
            .field(
                "Plazo de pago",
                po.getSupplier().getPaymentTermsDays() == 0
                    ? "Contado"
                    : po.getSupplier().getPaymentTermsDays() + " días")
            .field(
                "Origen",
                po.getCreatedByUserId() == null
                    ? "Generada por Xperp IA"
                    : "Creada por " + users.nameOf(po.getCreatedByUserId()))
            .table(
                new String[] {"SKU", "Producto", "Cantidad", "Costo unitario", "Total"},
                new float[] {1.2f, 4f, 1.3f, 1.8f, 1.8f});
    for (PurchaseOrderItem item : po.getItems()) {
      ProductDto p = catalog.product(item.getProductId());
      pdf.row(
          p.sku(),
          p.name(),
          String.valueOf(item.getQuantity()),
          Money.display(item.getUnitCost()),
          Money.display(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()))));
    }
    pdf.note("Total de la orden: " + Money.display(po.getTotal()));
    pdf.note("Documento generado con datos demo. Distribuidora La Ceiba es una empresa ficticia.");
    return pdf.render();
  }

  // ---------------------------------------------------------------- utilidades

  private PurchaseOrder lock(long id) {
    return orders.findForUpdate(id).orElseThrow(() -> BusinessException.notFound("OC", id));
  }

  private void recalc(PurchaseOrder po) {
    BigDecimal subtotal = BigDecimal.ZERO;
    for (PurchaseOrderItem item : po.getItems()) {
      subtotal = subtotal.add(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
    po.setSubtotal(Money.round(subtotal));
    po.setTotal(Money.round(subtotal));
  }

  private void addEvent(PurchaseOrder po, String status, String note) {
    PurchaseOrderEvent e = new PurchaseOrderEvent();
    e.setOrder(po);
    e.setStatus(status);
    e.setNote(truncate(note));
    e.setOccurredAt(Instant.now(clock));
    po.getEvents().add(e);
  }

  private void publishSent(PurchaseOrder po) {
    Map<Long, BigDecimal> byCategory = new HashMap<>();
    for (PurchaseOrderItem item : po.getItems()) {
      byCategory.merge(
          catalog.product(item.getProductId()).categoryId(),
          item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())),
          BigDecimal::add);
    }
    events.publishEvent(
        new PurchaseOrderSent(
            TenantContext.require(),
            po.getId(),
            po.getNumber(),
            po.getSupplier().getId(),
            po.getWarehouseId(),
            po.getTotal(),
            byCategory,
            SimTime.period(po.getSentAt()),
            po.getCreatedByUserId() == null));
  }

  private void publishStatus(PurchaseOrder po, String previous, String reason) {
    events.publishEvent(
        new PurchaseOrderStatusChanged(
            TenantContext.require(),
            po.getId(),
            po.getNumber(),
            po.getSupplier().getId(),
            po.getWarehouseId(),
            previous,
            po.getStatus(),
            reason,
            po.getExpectedAt()));
  }

  private void changed(PurchaseOrder po) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", po.getId());
    data.put("number", po.getNumber());
    data.put("status", po.getStatus());
    data.put("supplier_code", po.getSupplier().getCode());
    data.put("total", Money.format(po.getTotal()));
    sse.publish("purchase_order.changed", data, SseHub.Audience.ALL, String.valueOf(po.getId()));
  }

  private static String truncate(String s) {
    if (s == null) {
      return null;
    }
    return s.length() > 300 ? s.substring(0, 297) + "..." : s;
  }

  private void evict() {
    Long companyId = TenantContext.current();
    if (companyId != null) {
      supplierCache.remove(companyId);
    }
  }

  @Override
  public void clearCache() {
    supplierCache.clear();
  }

  private SupplierDto toDto(Supplier s, Map<Long, Integer> times) {
    return new SupplierDto(
        s.getId(),
        s.getCode(),
        s.getName(),
        s.getNit(),
        s.getCity(),
        s.getEmail(),
        s.getMinOrderAmount(),
        s.getPaymentTermsDays(),
        s.getOnTimeRate().doubleValue(),
        s.getFillRate().doubleValue(),
        s.isActive(),
        Map.copyOf(times));
  }

  private OfferDto toDto(SupplierProduct o) {
    return new OfferDto(
        supplier(o.getId().supplierId()),
        o.getId().productId(),
        o.getUnitCost(),
        o.getMinOrderCases(),
        o.isPreferred(),
        o.getPriceUpdatedAt());
  }

  private PurchaseOrderDto toDto(PurchaseOrder po) {
    return new PurchaseOrderDto(
        po.getId(),
        po.getNumber(),
        po.getSupplier().getId(),
        po.getSupplier().getCode(),
        po.getSupplier().getName(),
        po.getWarehouseId(),
        po.getStatus(),
        po.getStatusReason(),
        po.getSubtotal(),
        po.getTotal(),
        po.getExpectedAt(),
        po.getFirstExpectedAt(),
        po.getSentAt(),
        po.getConfirmedAt(),
        po.getReceivedAt(),
        po.getCreatedAt(),
        po.getCreatedByUserId(),
        po.getAiDecisionId(),
        po.getReplacesPoId(),
        po.getExternalId(),
        po.getItems().stream()
            .map(
                i ->
                    new PurchaseOrderDto.Line(
                        i.getId(),
                        i.getProductId(),
                        i.getQuantity(),
                        i.getQuantityShipped(),
                        i.getQuantityReceived(),
                        i.getUnitCost()))
            .toList(),
        po.getEvents().stream()
            .map(e -> new PurchaseOrderDto.Event(e.getStatus(), e.getNote(), e.getOccurredAt()))
            .toList());
  }
}
