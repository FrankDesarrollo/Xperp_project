package com.xperp.partners.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.xperp.partners.clock.BackendClock;
import com.xperp.partners.scenarios.ScenarioStore;
import com.xperp.partners.webhook.EventScheduler;
import com.xperp.partners.webhook.WebhookClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * El retraso del guion (AGENTS.md 2.13, día 5: PRV-06 se retrasa 4 días) manda sobre el azar.
 *
 * <p>En el ensayo del 2026-10-30, OC-2026-000002 (papel higiénico a PRV-06, el día 1) recibió un
 * retraso al azar el día 4, se reasignó, y el retraso del guion del día 5 no encontró ninguna orden
 * a la que aplicarse: el escenario se vio por suerte.
 */
@SpringBootTest(properties = {"PARTNERS_API_TOKEN=test-token", "WEBHOOK_SECRET=test-secret"})
class ScriptedDelayTest {

  private static final Instant DAY_1_0800 = Instant.parse("2026-11-01T13:00:00Z");
  private static final List<SupplierService.Item> PAPER =
      List.of(new SupplierService.Item("ASE-007", 600, "23827"));

  @MockitoBean BackendClock clock;
  @MockitoBean WebhookClient webhooks;

  @Autowired SupplierService suppliers;
  @Autowired ScenarioStore scenarios;
  @Autowired EventScheduler scheduler;

  @BeforeEach
  void setUp() {
    when(clock.now()).thenReturn(DAY_1_0800);
    suppliers.clear();
    scenarios.clear();
    scheduler.clear();
  }

  private boolean randomlyDelayed(String externalId) {
    return !scheduler
        .pending(e -> e.tag().equals(externalId) && "delayed".equals(e.kind()))
        .isEmpty();
  }

  @Test
  void sinGuionLaOcDelEnsayoSeRetrasaAlAzar() {
    SupplierService.Order order = suppliers.receive("PRV-06", "OC-2026-000002", "CAL-01", PAPER);

    assertThat(randomlyDelayed(order.externalId)).isTrue();
  }

  @Test
  void conUnRetrasoDelGuionAntesDeLaEntregaNoSeSumaOtroAlAzar() {
    scenarios.replace(
        List.of(
            Map.of(
                "type", "supplier_delay",
                "supplier", "PRV-06",
                "at", "2026-11-05T15:00:00Z",
                "delay_days", 4,
                "reason", "Retraso en el despacho desde Medellín")));

    SupplierService.Order order = suppliers.receive("PRV-06", "OC-2026-000002", "CAL-01", PAPER);

    assertThat(randomlyDelayed(order.externalId)).isFalse();
  }

  @Test
  void elGuionDeOtroProveedorNoApagaElAzar() {
    scenarios.replace(
        List.of(
            Map.of(
                "type", "supplier_delay",
                "supplier", "PRV-09",
                "at", "2026-11-05T15:00:00Z",
                "delay_days", 4,
                "reason", "Otro proveedor")));

    SupplierService.Order order = suppliers.receive("PRV-06", "OC-2026-000002", "CAL-01", PAPER);

    assertThat(randomlyDelayed(order.externalId)).isTrue();
  }
}
