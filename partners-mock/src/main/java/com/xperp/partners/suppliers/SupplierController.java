package com.xperp.partners.suppliers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API de proveedores simulados (contracts/partners.yaml). */
@RestController
class SupplierController {

  record OrderRequest(String poNumber, String warehouseCode, List<SupplierService.Item> items) {}

  private final SupplierService suppliers;

  SupplierController(SupplierService suppliers) {
    this.suppliers = suppliers;
  }

  @PostMapping("/suppliers/{code}/orders")
  ResponseEntity<Map<String, Object>> create(
      @PathVariable String code, @RequestBody OrderRequest request) {
    if (!suppliers.exists(code)) {
      return error(HttpStatus.NOT_FOUND, "Proveedor desconocido: " + code);
    }
    if (request.items() == null || request.items().isEmpty() || request.poNumber() == null) {
      return error(HttpStatus.BAD_REQUEST, "La orden necesita número y líneas");
    }
    SupplierService.Order order =
        suppliers.receive(code, request.poNumber(), request.warehouseCode(), request.items());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("external_id", order.externalId, "status", "received"));
  }

  @GetMapping("/suppliers/{code}/orders/{externalId}")
  ResponseEntity<Map<String, Object>> get(
      @PathVariable String code, @PathVariable String externalId) {
    return suppliers
        .order(externalId)
        .filter(o -> o.supplier.equals(code))
        .<ResponseEntity<Map<String, Object>>>map(
            o -> {
              Map<String, Object> body = new LinkedHashMap<>();
              body.put("external_id", o.externalId);
              body.put("po_number", o.poNumber);
              body.put("status", o.status);
              body.put(
                  "expected_at",
                  o.expectedAt == null
                      ? null
                      : o.expectedAt.atZone(SupplierService.ZONE).toOffsetDateTime().toString());
              body.put("reason", o.reason);
              body.put("items", o.items);
              return ResponseEntity.ok(body);
            })
        .orElseGet(() -> error(HttpStatus.NOT_FOUND, "Orden no encontrada"));
  }

  @GetMapping("/suppliers/{code}/catalog")
  ResponseEntity<?> catalog(@PathVariable String code) {
    if (!suppliers.exists(code)) {
      return error(HttpStatus.NOT_FOUND, "Proveedor desconocido: " + code);
    }
    return ResponseEntity.ok(suppliers.catalog(code));
  }

  static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("code", status.name().toLowerCase(), "message", message));
  }
}
