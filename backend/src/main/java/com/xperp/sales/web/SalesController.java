package com.xperp.sales.web;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.sales.api.CreateSalesOrder;
import com.xperp.sales.api.CustomerDto;
import com.xperp.sales.api.SalesLine;
import com.xperp.sales.api.SalesOrderDto;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.contract.api.SalesApi;
import com.xperp.shared.contract.model.Customer;
import com.xperp.shared.contract.model.CustomerPage;
import com.xperp.shared.contract.model.ReasonRequest;
import com.xperp.shared.contract.model.SalesOrder;
import com.xperp.shared.contract.model.SalesOrderItem;
import com.xperp.shared.contract.model.SalesOrderPage;
import com.xperp.shared.contract.model.SalesOrderRequest;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.time.SimTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SalesController implements SalesApi {

  private final SalesService sales;
  private final CatalogService catalog;
  private final InventoryService inventory;

  SalesController(SalesService sales, CatalogService catalog, InventoryService inventory) {
    this.sales = sales;
    this.catalog = catalog;
    this.inventory = inventory;
  }

  @Override
  public ResponseEntity<CustomerPage> listCustomers(
      String q, Long warehouseId, Integer page, Integer size) {
    PageResult<Customer> result =
        sales.customers(q, warehouseId, page, size).map(SalesController::toModel);
    return ResponseEntity.ok(
        new CustomerPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  public ResponseEntity<SalesOrderPage> listSalesOrders(
      String status, Long warehouseId, String q, Integer page, Integer size) {
    PageResult<SalesOrder> result =
        sales.orders(status, warehouseId, q, page, size).map(o -> toModel(o, false));
    return ResponseEntity.ok(
        new SalesOrderPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager')")
  public ResponseEntity<SalesOrder> createSalesOrder(SalesOrderRequest request) {
    SalesOrderDto order =
        sales.create(
            new CreateSalesOrder(
                request.getCustomerId(),
                request.getItems().stream()
                    .map(i -> new SalesLine(i.getProductId(), i.getQuantity()))
                    .toList()));
    return ResponseEntity.status(HttpStatus.CREATED).body(toModel(order, true));
  }

  @Override
  public ResponseEntity<SalesOrder> getSalesOrder(Long id) {
    return ResponseEntity.ok(toModel(sales.order(id), true));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager')")
  public ResponseEntity<SalesOrder> cancelSalesOrder(Long id, ReasonRequest reason) {
    return ResponseEntity.ok(
        toModel(sales.cancel(id, reason == null ? null : reason.getReason()), true));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','manager')")
  public ResponseEntity<SalesOrder> releaseSalesOrder(Long id) {
    return ResponseEntity.ok(toModel(sales.release(id), true));
  }

  static Customer toModel(CustomerDto c) {
    return new Customer(
            c.id(),
            c.code(),
            c.name(),
            c.segmentCode(),
            c.segmentName(),
            c.city(),
            c.warehouseId(),
            c.active())
        .document(c.document())
        .address(c.address());
  }

  private SalesOrder toModel(SalesOrderDto o, boolean withItems) {
    SalesOrder model =
        new SalesOrder(
                o.id(),
                o.number(),
                o.customerId(),
                o.customerName(),
                o.customerSegment(),
                o.customerCity(),
                o.warehouseId(),
                inventory.warehouse(o.warehouseId()).code(),
                o.status(),
                Money.format(o.subtotal()),
                Money.format(o.discount()),
                Money.format(o.total()),
                SimTime.toOffset(o.orderedAt()),
                o.lines().size())
            .holdReason(o.holdReason())
            .promisedAt(SimTime.toOffset(o.promisedAt()));
    if (withItems) {
      Map<Long, ProductDto> products =
          catalog.productsById(
              o.lines().stream().map(l -> l.productId()).collect(Collectors.toList()));
      List<SalesOrderItem> items =
          o.lines().stream()
              .map(
                  l -> {
                    ProductDto p = products.get(l.productId());
                    return new SalesOrderItem(
                        l.id(),
                        l.productId(),
                        p.sku(),
                        p.name(),
                        l.quantity(),
                        l.quantityFulfilled(),
                        Money.format(l.unitPrice()),
                        Money.format(l.lineTotal()));
                  })
              .toList();
      model.setItems(items);
    }
    return model;
  }
}
