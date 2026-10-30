package com.xperp.sales.service;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.sales.api.CreateSalesOrder;
import com.xperp.sales.api.CustomerCommand;
import com.xperp.sales.api.CustomerDto;
import com.xperp.sales.api.CustomerSkuStats;
import com.xperp.sales.api.DailyDemand;
import com.xperp.sales.api.SalesLine;
import com.xperp.sales.api.SalesOrderDto;
import com.xperp.sales.api.SalesOrderLineDto;
import com.xperp.sales.api.SalesOrderScreener;
import com.xperp.sales.api.SalesService;
import com.xperp.sales.api.SegmentDto;
import com.xperp.sales.domain.Customer;
import com.xperp.sales.domain.SalesOrder;
import com.xperp.sales.domain.SalesOrderItem;
import com.xperp.sales.events.SalesOrderCancelled;
import com.xperp.sales.events.SalesOrderCreated;
import com.xperp.sales.events.SalesOrderReleased;
import com.xperp.sales.repository.CustomerRepository;
import com.xperp.sales.repository.SalesOrderRepository;
import com.xperp.sales.repository.SalesQueries;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.DocumentNumbers;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class SalesServiceImpl implements SalesService, ResettableCache {

  private static final Set<String> CANCELLABLE = Set.of("created", "on_hold", "picking", "packed");

  private final CustomerRepository customers;
  private final SalesOrderRepository orders;
  private final SalesQueries queries;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final List<SalesOrderScreener> screeners;
  private final DocumentNumbers numbers;
  private final ApplicationEventPublisher events;
  private final SseHub sse;
  private final Clock clock;
  private final Map<Long, Map<String, SegmentDto>> segmentCache = new ConcurrentHashMap<>();

  SalesServiceImpl(
      CustomerRepository customers,
      SalesOrderRepository orders,
      SalesQueries queries,
      CatalogService catalog,
      InventoryService inventory,
      List<SalesOrderScreener> screeners,
      DocumentNumbers numbers,
      ApplicationEventPublisher events,
      SseHub sse,
      Clock clock) {
    this.customers = customers;
    this.orders = orders;
    this.queries = queries;
    this.catalog = catalog;
    this.inventory = inventory;
    this.screeners = screeners;
    this.numbers = numbers;
    this.events = events;
    this.sse = sse;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- segmentos y clientes

  @Override
  public void createSegment(String code, String name, BigDecimal discountPct) {
    queries.insertSegment(code, name, discountPct);
    segmentCache.remove(TenantContext.require());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SegmentDto> segments() {
    return List.copyOf(segmentMap().values());
  }

  private Map<String, SegmentDto> segmentMap() {
    return segmentCache.computeIfAbsent(
        TenantContext.require(),
        k -> {
          Map<String, SegmentDto> map = new LinkedHashMap<>();
          queries.segments().forEach(s -> map.put(s.code(), s));
          return map;
        });
  }

  @Override
  public CustomerDto createCustomer(CustomerCommand c) {
    if (!segmentMap().containsKey(c.segmentCode())) {
      throw BusinessException.invalid("invalid_segment", "Segmento inválido: " + c.segmentCode());
    }
    Customer customer = new Customer();
    customer.setCode(c.code());
    customer.setName(c.name());
    customer.setDocument(c.document());
    customer.setSegmentCode(c.segmentCode());
    customer.setCity(c.city());
    customer.setAddress(c.address());
    customer.setWarehouseId(c.warehouseId());
    return toDto(customers.save(customer));
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerDto customer(long id) {
    return toDto(loadCustomer(id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomerDto> activeCustomers() {
    return customers.findByActiveTrueOrderByIdAsc().stream().map(this::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<CustomerDto> customers(String q, Long warehouseId, Integer page, Integer size) {
    return PageResult.of(
        customers.search(like(q), warehouseId, PageResult.pageable(page, size, Sort.by("name"))),
        this::toDto);
  }

  // ---------------------------------------------------------------- pedidos

  @Override
  public SalesOrderDto create(CreateSalesOrder command) {
    Customer customer = loadCustomer(command.customerId());
    if (!customer.isActive()) {
      throw BusinessException.conflict("inactive_customer", "El cliente está inactivo");
    }
    Map<Long, Integer> merged = new LinkedHashMap<>();
    for (SalesLine line : command.lines()) {
      if (line.quantity() <= 0) {
        throw BusinessException.invalid("invalid_quantity", "Las cantidades deben ser positivas");
      }
      merged.merge(line.productId(), line.quantity(), Integer::sum);
    }
    if (merged.isEmpty()) {
      throw BusinessException.invalid("empty_order", "El pedido no tiene líneas");
    }
    Map<Long, ProductDto> products = catalog.productsById(merged.keySet());
    if (products.size() != merged.size()) {
      throw BusinessException.invalid("invalid_product", "El pedido tiene productos inexistentes");
    }

    Instant now = Instant.now(clock);
    SalesOrder order = new SalesOrder();
    order.setNumber(numbers.next(DocumentNumbers.SALES_ORDER));
    order.setCustomer(customer);
    order.setWarehouseId(customer.getWarehouseId());
    order.setOrderedAt(now);
    order.setPromisedAt(now.plus(Duration.ofDays(2)));
    merged.forEach(
        (productId, qty) -> {
          SalesOrderItem item = new SalesOrderItem();
          item.setOrder(order);
          item.setProductId(productId);
          item.setQuantity(qty);
          item.setUnitPrice(products.get(productId).price());
          order.getItems().add(item);
        });

    Optional<String> holdReason = screen(customer, merged);
    if (holdReason.isPresent()) {
      order.setStatus("on_hold");
      order.setFlagged(true);
      order.setHoldReason(truncate(holdReason.get()));
      orders.save(order);
      recalculate(order, true);
    } else {
      order.setStatus("created");
      orders.save(order);
      reserveLines(order);
    }
    publishCreated(order);
    return toDto(order);
  }

  private Optional<String> screen(Customer customer, Map<Long, Integer> lines) {
    var request =
        new SalesOrderScreener.ScreeningRequest(
            customer.getId(),
            customer.getSegmentCode(),
            customer.getWarehouseId(),
            lines.entrySet().stream().map(e -> new SalesLine(e.getKey(), e.getValue())).toList());
    for (SalesOrderScreener screener : screeners) {
      Optional<String> reason = screener.screen(request);
      if (reason.isPresent()) {
        return reason;
      }
    }
    return Optional.empty();
  }

  /** Reserva línea por línea (en orden de producto para evitar bloqueos cruzados). */
  private void reserveLines(SalesOrder order) {
    int fulfilledTotal = 0;
    List<SalesOrderItem> sorted =
        order.getItems().stream()
            .sorted(Comparator.comparing(SalesOrderItem::getProductId))
            .toList();
    for (SalesOrderItem item : sorted) {
      int reserved =
          inventory.reserve(item.getProductId(), order.getWarehouseId(), item.getQuantity());
      item.setQuantityFulfilled(reserved);
      fulfilledTotal += reserved;
      int lost = item.getQuantity() - reserved;
      if (lost > 0) {
        inventory.recordLostSale(item.getProductId(), order.getWarehouseId(), order.getId(), lost);
      }
    }
    if (fulfilledTotal == 0) {
      order.setStatus("cancelled");
      order.setHoldReason("Sin existencias para atender el pedido");
    }
    recalculate(order, false);
  }

  /** Subtotal sobre lo que se va a despachar (o lo pedido, si está retenido) menos el descuento. */
  private void recalculate(SalesOrder order, boolean useRequested) {
    BigDecimal subtotal = BigDecimal.ZERO;
    for (SalesOrderItem item : order.getItems()) {
      int qty = useRequested ? item.getQuantity() : item.getQuantityFulfilled();
      subtotal = subtotal.add(item.getUnitPrice().multiply(BigDecimal.valueOf(qty)));
    }
    SegmentDto segment = segmentMap().get(order.getCustomer().getSegmentCode());
    BigDecimal pct = segment == null ? BigDecimal.ZERO : segment.discountPct();
    BigDecimal discount =
        subtotal.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    order.setSubtotal(Money.round(subtotal));
    order.setDiscount(discount);
    order.setTotal(Money.round(subtotal.subtract(discount)));
  }

  private void publishCreated(SalesOrder order) {
    events.publishEvent(
        new SalesOrderCreated(
            TenantContext.require(),
            order.getId(),
            order.getNumber(),
            order.getWarehouseId(),
            order.getStatus(),
            order.getTotal(),
            order.getHoldReason()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", order.getId());
    data.put("number", order.getNumber());
    data.put("customer_name", order.getCustomer().getName());
    data.put("warehouse_id", order.getWarehouseId());
    data.put("status", order.getStatus());
    data.put("total", Money.format(order.getTotal()));
    data.put("ordered_at", order.getOrderedAt().toString());
    sse.publish("sale.created", data);
  }

  @Override
  @Transactional(readOnly = true)
  public SalesOrderDto order(long id) {
    return toDto(
        orders.findWithItems(id).orElseThrow(() -> BusinessException.notFound("Pedido", id)));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<SalesOrderDto> orders(
      String status, Long warehouseId, String q, Integer page, Integer size) {
    String st = status == null || status.isBlank() ? null : status;
    return PageResult.of(
        orders.search(
            st,
            warehouseId,
            like(q),
            PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "orderedAt", "id"))),
        this::toDto);
  }

  @Override
  public SalesOrderDto cancel(long id, String reason) {
    SalesOrder order = lockOrder(id);
    if (!CANCELLABLE.contains(order.getStatus())) {
      throw BusinessException.conflict(
          "invalid_status", "No se puede cancelar un pedido en estado " + order.getStatus());
    }
    if (!"on_hold".equals(order.getStatus())) {
      for (SalesOrderItem item : order.getItems()) {
        inventory.releaseReservation(
            item.getProductId(), order.getWarehouseId(), item.getQuantityFulfilled());
      }
    }
    order.setStatus("cancelled");
    if (reason != null && !reason.isBlank()) {
      order.setHoldReason(truncate(reason));
    }
    events.publishEvent(
        new SalesOrderCancelled(TenantContext.require(), order.getId(), order.getNumber()));
    return toDto(order);
  }

  @Override
  public SalesOrderDto release(long id) {
    SalesOrder order = lockOrder(id);
    if (!"on_hold".equals(order.getStatus())) {
      throw BusinessException.conflict("invalid_status", "El pedido no está retenido");
    }
    order.setStatus("created");
    reserveLines(order);
    events.publishEvent(
        new SalesOrderReleased(TenantContext.require(), order.getId(), order.getNumber()));
    return toDto(order);
  }

  @Override
  public void markPicking(long id) {
    transition(id, Set.of("created"), "picking");
  }

  @Override
  public void markPacked(long id) {
    transition(id, Set.of("created", "picking"), "packed");
  }

  @Override
  public void markShipped(long id) {
    transition(id, Set.of("packed"), "shipped");
  }

  @Override
  public void markDelivered(long id) {
    transition(id, Set.of("shipped"), "delivered");
  }

  private void transition(long id, Set<String> from, String to) {
    SalesOrder order =
        orders.findById(id).orElseThrow(() -> BusinessException.notFound("Pedido", id));
    if (from.contains(order.getStatus())) {
      order.setStatus(to);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<DailyDemand> dailyDemand(LocalDate from, LocalDate to) {
    return queries.dailyDemand(from, to);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, CustomerSkuStats> customerHistory(
      long customerId, Collection<Long> productIds, Long excludeOrderId) {
    return queries.customerHistory(customerId, productIds, excludeOrderId);
  }

  // ---------------------------------------------------------------- utilidades

  private Customer loadCustomer(long id) {
    return customers.findById(id).orElseThrow(() -> BusinessException.notFound("Cliente", id));
  }

  private SalesOrder lockOrder(long id) {
    return orders.findForUpdate(id).orElseThrow(() -> BusinessException.notFound("Pedido", id));
  }

  private static String like(String q) {
    return q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
  }

  private static String truncate(String s) {
    return s.length() > 300 ? s.substring(0, 297) + "..." : s;
  }

  @Override
  public void clearCache() {
    segmentCache.clear();
  }

  private CustomerDto toDto(Customer c) {
    SegmentDto segment = segmentMap().get(c.getSegmentCode());
    return new CustomerDto(
        c.getId(),
        c.getCode(),
        c.getName(),
        c.getDocument(),
        c.getSegmentCode(),
        segment == null ? c.getSegmentCode() : segment.name(),
        c.getCity(),
        c.getAddress(),
        c.getWarehouseId(),
        c.isActive());
  }

  private SalesOrderDto toDto(SalesOrder o) {
    Customer c = o.getCustomer();
    return new SalesOrderDto(
        o.getId(),
        o.getNumber(),
        c.getId(),
        c.getName(),
        c.getSegmentCode(),
        c.getCity(),
        o.getWarehouseId(),
        o.getStatus(),
        o.getSubtotal(),
        o.getDiscount(),
        o.getTotal(),
        o.getHoldReason(),
        o.isFlagged(),
        o.getOrderedAt(),
        o.getPromisedAt(),
        o.getItems().stream()
            .map(
                i ->
                    new SalesOrderLineDto(
                        i.getId(),
                        i.getProductId(),
                        i.getQuantity(),
                        i.getQuantityFulfilled(),
                        i.getUnitPrice()))
            .toList());
  }
}
