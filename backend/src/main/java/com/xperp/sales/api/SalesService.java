package com.xperp.sales.api;

import com.xperp.shared.persistence.PageResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** API pública de ventas: segmentos, clientes y pedidos. */
public interface SalesService {

  void createSegment(String code, String name, BigDecimal discountPct);

  List<SegmentDto> segments();

  CustomerDto createCustomer(CustomerCommand command);

  CustomerDto customer(long id);

  List<CustomerDto> activeCustomers();

  PageResult<CustomerDto> customers(String q, Long warehouseId, Integer page, Integer size);

  /** Crea el pedido, lo pasa por los filtros de anomalías y reserva stock. */
  SalesOrderDto create(CreateSalesOrder command);

  SalesOrderDto order(long id);

  PageResult<SalesOrderDto> orders(
      String status, Long warehouseId, String q, Integer page, Integer size);

  SalesOrderDto cancel(long id, String reason);

  /** Libera un pedido retenido: reserva stock y lo deja listo para alistar. */
  SalesOrderDto release(long id);

  void markPicking(long id);

  void markPacked(long id);

  void markShipped(long id);

  void markDelivered(long id);

  /** Demanda diaria pedida por SKU y bodega (incluye lo que no se pudo despachar). */
  List<DailyDemand> dailyDemand(LocalDate from, LocalDate to);

  /** Historial de un cliente por SKU, sin contar el pedido indicado. */
  Map<Long, CustomerSkuStats> customerHistory(
      long customerId, Collection<Long> productIds, Long excludeOrderId);
}
