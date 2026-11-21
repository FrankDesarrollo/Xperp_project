package com.xperp;

import static org.assertj.core.api.Assertions.assertThat;

import com.xperp.ai.agent.FinancialAdvisor;
import com.xperp.ai.decisions.AiUsageReport;
import com.xperp.ai.decisions.ApprovalService;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.tenancy.TenantDirectory;
import com.xperp.simulation.clock.SimulationEngine;
import java.time.LocalDate;
import java.util.List;
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
 * 10:00 y verifica que la IA tomó las decisiones del guion (AGENTS.md, 2.13), que nada financiero
 * se ejecutó sin una persona (ADR 0004) y que la aprobación en lote y la revisión financiera
 * funcionan. partners-mock no está levantado: las OC aprobadas quedan pendientes de reintento de
 * envío.
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
  @Autowired ApprovalService approvals;
  @Autowired FinancialAdvisor financial;
  @Autowired AiUsageReport usageReport;

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

    // ADR 0004: nada que mueva dinero o activos se ejecutó sin una persona
    assertThat(count(AUTONOMOUS_FINANCIAL)).isZero();
    // Las OC esperan aprobación y llevan su impacto (monto, presupuesto, margen, riesgo)
    assertThat(
            count(
                """
                select count(*) from ai_decisions
                where action_type = 'create_purchase_order' and status = 'pending_approval'
                  and jsonb_array_length(impact) >= 3
                """))
        .isPositive();
    assertThat(
            count(
                "select count(*) from ai_decisions where action_type = 'create_purchase_order'"
                    + " and status = 'executed'"))
        .isZero();

    long company = tenants.defaultCompanyId().orElseThrow();
    TenantContext.run(
        company,
        () -> {
          // Aprobación en lote de lo que le toca a Compras: cada una se ejecuta con su aprobación
          List<Long> ids =
              jdbc.queryForList(
                  """
                  select a.id from approvals a join ai_decisions d on d.id = a.ai_decision_id
                  where a.status = 'pending' and a.requested_role = 'purchasing'
                    and d.action_type = 'create_purchase_order'
                  """,
                  Long.class);
          assertThat(ids).isNotEmpty();
          ApprovalService.BatchResult r = approvals.approveAll(ids, null, "purchasing", "lote");
          assertThat(r.approved()).hasSameSizeAs(ids);
          assertThat(r.failed()).isEmpty();
          assertThat(r.amount()).isPositive();
          // Compras no puede decidir lo que le corresponde a Gerencia
          List<Long> managerOnly =
              jdbc.queryForList(
                  "select id from approvals where status = 'pending' and requested_role = 'manager'",
                  Long.class);
          if (!managerOnly.isEmpty()) {
            assertThat(approvals.approveAll(managerOnly, null, "purchasing", null).failed())
                .hasSameSizeAs(managerOnly);
          }

          // Revisión financiera a pedido: reglas, sin ejecutar nada
          long reviewId = financial.review("user_request");
          assertThat(
                  count(
                      "select count(*) from ai_decisions where id = "
                          + reviewId
                          + " and decision_type = 'financial_review' and status = 'executed'"
                          + " and proposed_action ? 'findings'"))
              .isEqualTo(1);

          AiUsageReport.Report usage =
              usageReport.report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
          assertThat(usage.autonomousFinancialActions()).isZero();
          assertThat(usage.byTrigger().get("event")).isPositive();
          assertThat(usage.byTrigger().get("request")).isPositive();
          // Casi todo lo resuelven las reglas; el LLM (aquí, el mock) solo en empates y excepciones
          assertThat(usage.decidedByRules() + usage.decidedWithLlm()).isEqualTo(usage.decisions());
          assertThat(usage.decidedByRules()).isGreaterThan(usage.decidedWithLlm());
        });
    awaitEvents();
    assertThat(count(AUTONOMOUS_FINANCIAL)).isZero();
  }

  /** Espera a que terminen los listeners asíncronos antes de que se apague la base de pruebas. */
  private void awaitEvents() {
    for (int i = 0; i < 100; i++) {
      if (count("select count(*) from event_publication where completion_date is null") == 0) {
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  /** Acciones financieras que se ejecutaron por política sin aprobación heredada. */
  private static final String AUTONOMOUS_FINANCIAL =
      """
      select count(*) from ai_decisions
      where action_type in ('create_purchase_order', 'reassign_purchase_order',
                            'cancel_purchase_order', 'propose_stock_transfer', 'adjust_inventory',
                            'apply_budget_plan', 'create_shipment')
        and policy_result = 'executed' and approved_in_decision_id is null
      """;

  private int count(String sql) {
    Integer n = jdbc.queryForObject(sql, Integer.class);
    return n == null ? 0 : n;
  }
}
