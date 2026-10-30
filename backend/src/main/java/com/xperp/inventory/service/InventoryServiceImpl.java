package com.xperp.inventory.service;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.CountDto;
import com.xperp.inventory.api.InTransitProvider;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.MovementDto;
import com.xperp.inventory.api.StockDto;
import com.xperp.inventory.api.StockKey;
import com.xperp.inventory.api.StockParams;
import com.xperp.inventory.api.TransferDto;
import com.xperp.inventory.api.TransferLine;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.inventory.domain.InventoryCount;
import com.xperp.inventory.domain.InventoryMovement;
import com.xperp.inventory.domain.LostSale;
import com.xperp.inventory.domain.Stock;
import com.xperp.inventory.domain.StockId;
import com.xperp.inventory.domain.StockTransfer;
import com.xperp.inventory.domain.StockTransferItem;
import com.xperp.inventory.domain.Warehouse;
import com.xperp.inventory.events.InventoryCountRecorded;
import com.xperp.inventory.events.StockBelowSafety;
import com.xperp.inventory.repository.CountRepository;
import com.xperp.inventory.repository.LostSaleRepository;
import com.xperp.inventory.repository.MovementRepository;
import com.xperp.inventory.repository.StockRepository;
import com.xperp.inventory.repository.TransferRepository;
import com.xperp.inventory.repository.WarehouseRepository;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.DocumentNumbers;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.security.CurrentUser;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
class InventoryServiceImpl implements InventoryService, ResettableCache {

  static final String SYSTEM_USER = "Xperp IA";

  private final WarehouseRepository warehouses;
  private final StockRepository stocks;
  private final MovementRepository movements;
  private final CountRepository counts;
  private final TransferRepository transfers;
  private final LostSaleRepository lostSales;
  private final CatalogService catalog;
  private final List<InTransitProvider> inTransitProviders;
  private final DocumentNumbers numbers;
  private final ApplicationEventPublisher events;
  private final SseHub sse;
  private final Clock clock;
  private final Map<Long, List<WarehouseDto>> warehouseCache = new ConcurrentHashMap<>();

  InventoryServiceImpl(
      WarehouseRepository warehouses,
      StockRepository stocks,
      MovementRepository movements,
      CountRepository counts,
      TransferRepository transfers,
      LostSaleRepository lostSales,
      CatalogService catalog,
      List<InTransitProvider> inTransitProviders,
      DocumentNumbers numbers,
      ApplicationEventPublisher events,
      SseHub sse,
      Clock clock) {
    this.warehouses = warehouses;
    this.stocks = stocks;
    this.movements = movements;
    this.counts = counts;
    this.transfers = transfers;
    this.lostSales = lostSales;
    this.catalog = catalog;
    this.inTransitProviders = inTransitProviders;
    this.numbers = numbers;
    this.events = events;
    this.sse = sse;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- bodegas

  @Override
  @Transactional(readOnly = true)
  public List<WarehouseDto> warehouses() {
    return warehouseCache.computeIfAbsent(
        TenantContext.require(),
        k -> warehouses.findAllByOrderByIdAsc().stream().map(InventoryServiceImpl::toDto).toList());
  }

  @Override
  @Transactional(readOnly = true)
  public WarehouseDto warehouse(long id) {
    return warehouses().stream()
        .filter(w -> w.id() == id)
        .findFirst()
        .orElseThrow(() -> BusinessException.notFound("Bodega", id));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WarehouseDto> warehouseByCode(String code) {
    return warehouses().stream().filter(w -> w.code().equalsIgnoreCase(code)).findFirst();
  }

  @Override
  public WarehouseDto createWarehouse(String code, String name, String city, String address) {
    Warehouse w = new Warehouse();
    w.setCode(code);
    w.setName(name);
    w.setCity(city);
    w.setAddress(address);
    WarehouseDto dto = toDto(warehouses.save(w));
    warehouseCache.remove(TenantContext.require());
    return dto;
  }

  // ---------------------------------------------------------------- existencias

  @Override
  @Transactional(readOnly = true)
  public StockDto stock(long productId, long warehouseId) {
    return toDto(load(productId, warehouseId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockDto> allStock() {
    return stocks.findAll().stream().map(InventoryServiceImpl::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<InventoryRow> rows(Long warehouseId) {
    List<Stock> list =
        warehouseId == null ? stocks.findAll() : stocks.findByIdWarehouseId(warehouseId);
    Map<StockKey, Integer> inTransit = inTransit();
    Map<Long, String> whCodes = new HashMap<>();
    warehouses().forEach(w -> whCodes.put(w.id(), w.code()));
    List<InventoryRow> rows = new ArrayList<>();
    for (Stock s : list) {
      ProductDto p = catalog.product(s.getId().productId());
      rows.add(toRow(toDto(s), p, whCodes.get(s.getId().warehouseId()), inTransit));
    }
    rows.sort(Comparator.comparing(InventoryRow::sku).thenComparing(InventoryRow::warehouseCode));
    return rows;
  }

  @Override
  @Transactional(readOnly = true)
  public InventoryRow row(long productId, long warehouseId) {
    Stock s = load(productId, warehouseId);
    return toRow(toDto(s), catalog.product(productId), warehouse(warehouseId).code(), inTransit());
  }

  private InventoryRow toRow(
      StockDto s, ProductDto p, String whCode, Map<StockKey, Integer> inTransit) {
    return new InventoryRow(
        s,
        p.sku(),
        p.name(),
        p.categoryCode(),
        p.unitsPerCase(),
        whCode,
        inTransit.getOrDefault(s.key(), 0),
        status(s),
        p.cost());
  }

  /** Semáforo: quiebre, bajo, normal o exceso (AGENTS.md, sección 11.2). */
  static String status(StockDto s) {
    if (s.available() <= 0) {
      return InventoryRow.STOCKOUT;
    }
    if (s.available() <= s.reorderPoint()) {
      return InventoryRow.LOW;
    }
    if (s.maxStock() > 0 && s.onHand() > s.maxStock()) {
      return InventoryRow.OVER;
    }
    return InventoryRow.OK;
  }

  /** En tránsito: OC abiertas (proveedor externo) y traslados en camino. */
  private Map<StockKey, Integer> inTransit() {
    Map<StockKey, Integer> total = new HashMap<>();
    for (InTransitProvider provider : inTransitProviders) {
      provider.inboundUnits().forEach((k, v) -> total.merge(k, v, Integer::sum));
    }
    for (StockTransfer t : transfers.findByStatusIn(Set.of("requested", "in_transit"))) {
      for (StockTransferItem item : t.getItems()) {
        total.merge(
            new StockKey(item.getProductId(), t.getToWarehouseId()),
            item.getQuantity(),
            Integer::sum);
      }
    }
    return total;
  }

  @Override
  public void setInitialStock(long productId, long warehouseId, int quantity, BigDecimal cost) {
    Stock stock = stocks.findById(new StockId(productId, warehouseId)).orElseGet(Stock::new);
    if (stock.getId() == null) {
      stock.setId(new StockId(productId, warehouseId));
    }
    stock.setOnHand(quantity);
    stock.setReserved(0);
    stocks.save(stock);
    record(stock, "adjustment", quantity, cost, null, null, "Inventario inicial", SYSTEM_USER);
  }

  @Override
  public int reserve(long productId, long warehouseId, int quantity) {
    Stock stock = lock(productId, warehouseId);
    int before = stock.available();
    int reserved = Math.max(0, Math.min(quantity, before));
    if (reserved > 0) {
      stock.setReserved(stock.getReserved() + reserved);
      changed(stock);
    }
    int after = stock.available();
    if (stock.getSafetyStock() > 0
        && after < stock.getSafetyStock()
        && before >= stock.getSafetyStock()) {
      events.publishEvent(
          new StockBelowSafety(
              TenantContext.require(), productId, warehouseId, after, stock.getSafetyStock()));
    }
    return reserved;
  }

  @Override
  public void releaseReservation(long productId, long warehouseId, int quantity) {
    if (quantity <= 0) {
      return;
    }
    Stock stock = lock(productId, warehouseId);
    stock.setReserved(Math.max(0, stock.getReserved() - quantity));
    changed(stock);
  }

  @Override
  public void dispatch(
      long productId, long warehouseId, int quantity, String referenceType, long referenceId) {
    if (quantity <= 0) {
      return;
    }
    Stock stock = lock(productId, warehouseId);
    int qty = Math.min(quantity, stock.getOnHand());
    stock.setOnHand(stock.getOnHand() - qty);
    stock.setReserved(Math.max(0, stock.getReserved() - quantity));
    ProductDto product = catalog.product(productId);
    record(stock, "sale", -qty, product.cost(), referenceType, referenceId, null, SYSTEM_USER);
    changed(stock);
  }

  @Override
  public void receive(
      long productId,
      long warehouseId,
      int quantity,
      BigDecimal unitCost,
      String referenceType,
      long referenceId,
      String receivedBy) {
    if (quantity <= 0) {
      return;
    }
    Stock stock = lock(productId, warehouseId);
    stock.setOnHand(stock.getOnHand() + quantity);
    record(
        stock,
        "purchase_receipt",
        quantity,
        unitCost,
        referenceType,
        referenceId,
        null,
        receivedBy == null ? SYSTEM_USER : receivedBy);
    changed(stock);
  }

  @Override
  public MovementDto adjust(
      long productId,
      long warehouseId,
      int delta,
      String reason,
      String referenceType,
      Long referenceId) {
    Stock stock = lock(productId, warehouseId);
    if (stock.getOnHand() + delta < stock.getReserved()) {
      throw BusinessException.conflict(
          "insufficient_stock", "El ajuste deja el inventario por debajo de lo reservado");
    }
    stock.setOnHand(stock.getOnHand() + delta);
    InventoryMovement m =
        record(
            stock,
            "adjustment",
            delta,
            catalog.product(productId).cost(),
            referenceType,
            referenceId,
            reason,
            CurrentUser.nameOr(SYSTEM_USER));
    changed(stock);
    return toDto(m);
  }

  @Override
  public void recordLostSale(long productId, long warehouseId, Long salesOrderId, int quantity) {
    if (quantity <= 0) {
      return;
    }
    LostSale lost = new LostSale();
    lost.setProductId(productId);
    lost.setWarehouseId(warehouseId);
    lost.setSalesOrderId(salesOrderId);
    lost.setQuantity(quantity);
    lost.setOccurredAt(Instant.now(clock));
    lostSales.save(lost);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<MovementDto> movements(
      long productId, long warehouseId, Integer page, Integer size) {
    load(productId, warehouseId);
    return PageResult.of(
        movements.findByProductIdAndWarehouseIdOrderByOccurredAtDescIdDesc(
            productId, warehouseId, PageResult.pageable(page, size, Sort.unsorted())),
        InventoryServiceImpl::toDto);
  }

  // ---------------------------------------------------------------- parámetros

  @Override
  public void updateParams(long productId, long warehouseId, StockParams params) {
    Stock stock = load(productId, warehouseId);
    stock.setSafetyStock(params.safetyStock());
    stock.setReorderPoint(params.reorderPoint());
    stock.setMaxStock(params.maxStock());
    stock.setCoverDays(params.coverDays());
    stock.setAvgDailyDemand(params.avgDailyDemand());
    stock.setParamsUpdatedAt(Instant.now(clock));
  }

  @Override
  public void setFlags(long productId, long warehouseId, boolean promotion, boolean blocked) {
    Stock stock = load(productId, warehouseId);
    stock.setPromotionSuggested(promotion);
    stock.setReorderBlocked(blocked);
    changed(stock);
  }

  // ---------------------------------------------------------------- conteos

  @Override
  public CountDto recordCount(long productId, long warehouseId, int countedQty, boolean recount) {
    if (countedQty < 0) {
      throw BusinessException.invalid("invalid_count", "El conteo no puede ser negativo");
    }
    Stock stock = load(productId, warehouseId);
    InventoryCount count = new InventoryCount();
    count.setProductId(productId);
    count.setWarehouseId(warehouseId);
    count.setSystemQty(stock.getOnHand());
    count.setCountedQty(countedQty);
    count.setStatus(countedQty == stock.getOnHand() ? "recorded" : "discrepancy");
    count.setCountedBy(CurrentUser.nameOr(SYSTEM_USER));
    count.setCountedAt(Instant.now(clock));
    counts.save(count);
    events.publishEvent(
        new InventoryCountRecorded(
            TenantContext.require(),
            count.getId(),
            productId,
            warehouseId,
            count.getSystemQty(),
            countedQty,
            recount));
    return toDto(count);
  }

  @Override
  @Transactional(readOnly = true)
  public CountDto count(long id) {
    return counts
        .findById(id)
        .map(InventoryServiceImpl::toDto)
        .orElseThrow(() -> BusinessException.notFound("Conteo", id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CountDto> recentCounts(Long warehouseId) {
    List<InventoryCount> list =
        warehouseId == null
            ? counts.findTop50ByOrderByCountedAtDescIdDesc()
            : counts.findTop50ByWarehouseIdOrderByCountedAtDescIdDesc(warehouseId);
    return list.stream().map(InventoryServiceImpl::toDto).toList();
  }

  @Override
  public void markCountAdjusted(long countId) {
    counts.findById(countId).ifPresent(c -> c.setStatus("adjusted"));
  }

  // ---------------------------------------------------------------- traslados

  @Override
  public TransferDto createTransfer(
      long fromWarehouseId, long toWarehouseId, List<TransferLine> lines, Long aiDecisionId) {
    if (fromWarehouseId == toWarehouseId) {
      throw BusinessException.invalid("invalid_transfer", "Origen y destino deben ser distintos");
    }
    warehouse(fromWarehouseId);
    warehouse(toWarehouseId);
    Map<Long, Integer> merged = new LinkedHashMap<>();
    lines.forEach(l -> merged.merge(l.productId(), l.quantity(), Integer::sum));
    StockTransfer t = new StockTransfer();
    t.setNumber(numbers.next(DocumentNumbers.STOCK_TRANSFER));
    t.setFromWarehouseId(fromWarehouseId);
    t.setToWarehouseId(toWarehouseId);
    t.setStatus("requested");
    t.setAiDecisionId(aiDecisionId);
    merged.forEach(
        (productId, qty) -> {
          Stock origin = load(productId, fromWarehouseId);
          if (qty <= 0 || qty > origin.available()) {
            throw BusinessException.conflict(
                "insufficient_stock",
                "No hay disponible suficiente de %s en la bodega de origen"
                    .formatted(catalog.product(productId).sku()));
          }
          StockTransferItem item = new StockTransferItem();
          item.setTransfer(t);
          item.setProductId(productId);
          item.setQuantity(qty);
          t.getItems().add(item);
        });
    transfers.save(t);
    // Se reserva en origen para que nadie venda lo que va a salir.
    merged.forEach((productId, qty) -> reserve(productId, fromWarehouseId, qty));
    return toDto(t);
  }

  @Override
  @Transactional(readOnly = true)
  public TransferDto transfer(long id) {
    return toDto(loadTransfer(id));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<TransferDto> transfers(String status, Integer page, Integer size) {
    var pageable = PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
    var result =
        status == null || status.isBlank()
            ? transfers.findAll(pageable)
            : transfers.findByStatus(status, pageable);
    return PageResult.of(result, InventoryServiceImpl::toDto);
  }

  @Override
  public TransferDto shipTransfer(long id) {
    StockTransfer t = loadTransfer(id);
    if (!"requested".equals(t.getStatus())) {
      throw BusinessException.conflict("invalid_status", "El traslado no está pendiente de salida");
    }
    for (StockTransferItem item : t.getItems()) {
      Stock origin = lock(item.getProductId(), t.getFromWarehouseId());
      int qty = Math.min(item.getQuantity(), origin.getOnHand());
      origin.setOnHand(origin.getOnHand() - qty);
      origin.setReserved(Math.max(0, origin.getReserved() - item.getQuantity()));
      record(
          origin,
          "transfer_out",
          -qty,
          catalog.product(item.getProductId()).cost(),
          "stock_transfer",
          t.getId(),
          t.getNumber(),
          CurrentUser.nameOr(SYSTEM_USER));
      changed(origin);
    }
    t.setStatus("in_transit");
    t.setShippedAt(Instant.now(clock));
    return toDto(t);
  }

  @Override
  public TransferDto receiveTransfer(long id) {
    StockTransfer t = loadTransfer(id);
    if (!"in_transit".equals(t.getStatus())) {
      throw BusinessException.conflict("invalid_status", "El traslado no está en camino");
    }
    for (StockTransferItem item : t.getItems()) {
      Stock dest = lock(item.getProductId(), t.getToWarehouseId());
      dest.setOnHand(dest.getOnHand() + item.getQuantity());
      record(
          dest,
          "transfer_in",
          item.getQuantity(),
          catalog.product(item.getProductId()).cost(),
          "stock_transfer",
          t.getId(),
          t.getNumber(),
          CurrentUser.nameOr(SYSTEM_USER));
      changed(dest);
    }
    t.setStatus("received");
    t.setReceivedAt(Instant.now(clock));
    return toDto(t);
  }

  @Override
  public TransferDto cancelTransfer(long id) {
    StockTransfer t = loadTransfer(id);
    if (!"requested".equals(t.getStatus())) {
      throw BusinessException.conflict(
          "not_reversible", "Solo se puede cancelar un traslado que no ha salido");
    }
    t.getItems()
        .forEach(
            i -> releaseReservation(i.getProductId(), t.getFromWarehouseId(), i.getQuantity()));
    t.setStatus("cancelled");
    return toDto(t);
  }

  // ---------------------------------------------------------------- utilidades

  private Stock load(long productId, long warehouseId) {
    return stocks
        .findById(new StockId(productId, warehouseId))
        .orElseThrow(() -> BusinessException.notFound("Existencia", productId + "/" + warehouseId));
  }

  private Stock lock(long productId, long warehouseId) {
    return stocks
        .findForUpdate(new StockId(productId, warehouseId))
        .orElseThrow(() -> BusinessException.notFound("Existencia", productId + "/" + warehouseId));
  }

  private StockTransfer loadTransfer(long id) {
    return transfers.findById(id).orElseThrow(() -> BusinessException.notFound("Traslado", id));
  }

  private InventoryMovement record(
      Stock stock,
      String type,
      int quantity,
      BigDecimal unitCost,
      String referenceType,
      Long referenceId,
      String note,
      String by) {
    InventoryMovement m = new InventoryMovement();
    m.setProductId(stock.getId().productId());
    m.setWarehouseId(stock.getId().warehouseId());
    m.setType(type);
    m.setQuantity(quantity);
    m.setBalanceAfter(stock.getOnHand());
    m.setUnitCost(unitCost);
    m.setReferenceType(referenceType);
    m.setReferenceId(referenceId);
    m.setNote(note);
    m.setOccurredAt(Instant.now(clock));
    m.setCreatedBy(by);
    return movements.save(m);
  }

  private void changed(Stock s) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("product_id", s.getId().productId());
    data.put("warehouse_id", s.getId().warehouseId());
    data.put("on_hand", s.getOnHand());
    data.put("reserved", s.getReserved());
    data.put("available", s.available());
    sse.publish(
        "inventory.changed",
        data,
        SseHub.Audience.ALL,
        s.getId().productId() + ":" + s.getId().warehouseId());
  }

  @Override
  public void clearCache() {
    warehouseCache.clear();
  }

  static WarehouseDto toDto(Warehouse w) {
    return new WarehouseDto(w.getId(), w.getCode(), w.getName(), w.getCity(), w.getAddress());
  }

  static StockDto toDto(Stock s) {
    return new StockDto(
        s.getId().productId(),
        s.getId().warehouseId(),
        s.getOnHand(),
        s.getReserved(),
        s.getSafetyStock(),
        s.getReorderPoint(),
        s.getMaxStock(),
        s.getCoverDays(),
        s.getAvgDailyDemand(),
        s.isPromotionSuggested(),
        s.isReorderBlocked(),
        s.getParamsUpdatedAt());
  }

  static MovementDto toDto(InventoryMovement m) {
    return new MovementDto(
        m.getId(),
        m.getProductId(),
        m.getWarehouseId(),
        m.getType(),
        m.getQuantity(),
        m.getBalanceAfter(),
        m.getUnitCost(),
        m.getReferenceType(),
        m.getReferenceId(),
        m.getNote(),
        m.getOccurredAt(),
        m.getCreatedBy());
  }

  static CountDto toDto(InventoryCount c) {
    return new CountDto(
        c.getId(),
        c.getProductId(),
        c.getWarehouseId(),
        c.getSystemQty(),
        c.getCountedQty(),
        c.getStatus(),
        c.getCountedBy(),
        c.getCountedAt());
  }

  static TransferDto toDto(StockTransfer t) {
    return new TransferDto(
        t.getId(),
        t.getNumber(),
        t.getFromWarehouseId(),
        t.getToWarehouseId(),
        t.getStatus(),
        t.getAiDecisionId(),
        t.getShippedAt(),
        t.getReceivedAt(),
        t.getCreatedAt(),
        t.getItems().stream()
            .map(i -> new TransferLine(i.getProductId(), i.getQuantity()))
            .toList());
  }
}
