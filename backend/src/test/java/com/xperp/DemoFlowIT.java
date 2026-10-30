package com.xperp;

import static org.assertj.core.api.Assertions.assertThat;

import com.xperp.shared.tenancy.TenantDirectory;
import com.xperp.simulation.clock.SimulationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flujo de punta a punta contra PostgreSQL real: carga la empresa demo, avanza el día 1 hasta las
 * 10:00 y verifica que la IA tomó las decisiones del guion (AGENTS.md, 2.13). partners-mock no está
 * levantado: las OC quedan enviadas y pendientes de reintento, y la demo sigue funcionando.
 */
@Testcontainers
@ActiveProfiles("demo")
@SpringBootTest(
    properties = {
      "JWT_SECRET=clave-de-pruebas-de-al-menos-32-caracteres",
      "PARTNERS_API_TOKEN=test",
      "WEBHOOK_SECRET=test",
      "xperp.partners.api-url=http://localhost:1",
      "SIM_HISTORY_DAYS=35"
    })
class DemoFlowIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired SimulationEngine engine;
  @Autowired TenantDirectory tenants;

  @Test
  void demoLoadsAndAiActsOnDayOne() {
    assertThat(tenants.defaultCompanyId()).isPresent();
    assertThat(count("select count(*) from products")).isEqualTo(42);
    assertThat(count("select count(*) from customers")).isEqualTo(120);
    assertThat(count("select count(*) from sales_orders")).isGreaterThan(2000);
    assertThat(count("select count(*) from inventory where reorder_point > 0")).isGreaterThan(80);

    engine.advance(10);

    // Leche UHT a PRV-03, por tiempo de entrega
    assertThat(
            count(
                """
                select count(*) from purchase_orders po join suppliers s on s.id = po.supplier_id
                join purchase_order_items i on i.purchase_order_id = po.id
                join products p on p.id = i.product_id
                where s.code = 'PRV-03' and p.sku = 'BEB-001'
                """))
        .isEqualTo(1);
    // Pañales: traslado en vez de compra, esperando aprobación de gerencia
    assertThat(
            count(
                "select count(*) from ai_decisions where decision_type = 'stock_transfer' and status = 'pending_approval'"))
        .isEqualTo(1);
    // Cada pedido del día tiene su decisión de alistamiento
    assertThat(count("select count(*) from ai_decisions where decision_type = 'fulfillment'"))
        .isPositive();
    // Toda acción queda en la bitácora (regla 6)
    assertThat(count("select count(*) from ai_decisions where reasoning is null")).isZero();
  }

  private int count(String sql) {
    Integer n = jdbc.queryForObject(sql, Integer.class);
    return n == null ? 0 : n;
  }
}
