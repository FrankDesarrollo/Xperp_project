package com.xperp.partners.scenarios;

import com.xperp.partners.suppliers.SupplierService;
import com.xperp.partners.webhook.EventScheduler;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Control desde el backend: reinicio y eventos forzados del guion (contracts/partners.yaml). */
@RestController
class AdminController {

  private static final Logger log = LoggerFactory.getLogger(AdminController.class);

  private final ScenarioStore scenarios;
  private final SupplierService suppliers;
  private final EventScheduler scheduler;

  AdminController(ScenarioStore scenarios, SupplierService suppliers, EventScheduler scheduler) {
    this.scenarios = scenarios;
    this.suppliers = suppliers;
    this.scheduler = scheduler;
  }

  @PostMapping("/admin/reset")
  ResponseEntity<Void> reset() {
    scenarios.clear();
    suppliers.clear();
    scheduler.clear();
    log.info("partners-mock reiniciado");
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/admin/scenarios")
  ResponseEntity<Void> configure(@RequestBody List<Map<String, Object>> body) {
    scenarios.replace(body);
    log.info("{} eventos del guion configurados", body.size());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/admin/queue")
  ResponseEntity<Map<String, Object>> queue() {
    return ResponseEntity.ok(Map.of("pending_webhooks", scheduler.pending(e -> true).size()));
  }
}
