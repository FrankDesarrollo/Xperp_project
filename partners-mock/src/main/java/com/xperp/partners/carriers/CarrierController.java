package com.xperp.partners.carriers;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API de transportadoras simuladas (contracts/partners.yaml). */
@RestController
class CarrierController {

  record QuoteRequest(String originCity, String destinationCity, double weightKg) {}

  record ShipmentRequest(
      String salesOrderNumber,
      String originCity,
      String destinationCity,
      double weightKg,
      String service) {}

  private final CarrierService carriers;

  CarrierController(CarrierService carriers) {
    this.carriers = carriers;
  }

  @PostMapping("/carriers/{code}/quotes")
  ResponseEntity<?> quote(@PathVariable String code, @RequestBody QuoteRequest r) {
    if (!carriers.exists(code)) {
      return error(HttpStatus.NOT_FOUND, "Transportadora desconocida");
    }
    return carriers
        .quote(code, r.originCity(), r.destinationCity(), r.weightKg())
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(
            () -> error(HttpStatus.UNPROCESSABLE_ENTITY, "No cubrimos " + r.destinationCity()));
  }

  @PostMapping("/carriers/{code}/shipments")
  ResponseEntity<?> create(@PathVariable String code, @RequestBody ShipmentRequest r) {
    if (!carriers.exists(code)) {
      return error(HttpStatus.NOT_FOUND, "Transportadora desconocida");
    }
    String tracking =
        carriers.createShipment(
            code, r.salesOrderNumber(), r.originCity(), r.destinationCity(), r.weightKg());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("tracking_number", tracking));
  }

  static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("code", status.name().toLowerCase(), "message", message));
  }
}
