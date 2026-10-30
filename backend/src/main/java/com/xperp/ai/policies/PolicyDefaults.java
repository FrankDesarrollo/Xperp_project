package com.xperp.ai.policies;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Políticas de autonomía por defecto (AGENTS.md, secciones 8.4, 9.1 y 9.2). Se editan después desde
 * IA → Políticas.
 */
@Component
public class PolicyDefaults {

  private final AutonomyPolicyRepository policies;

  PolicyDefaults(AutonomyPolicyRepository policies) {
    this.policies = policies;
  }

  @Transactional
  public void create() {
    policies.deleteAll();
    add(
        "create_purchase_order",
        "OC de menos de $ 5.000.000: se ejecuta y se notifica",
        2,
        null,
        "5000000",
        null,
        Map.of());
    add(
        "create_purchase_order",
        "OC de $ 5.000.000 a $ 20.000.000: aprueba Compras",
        1,
        "5000000",
        "20000000.01",
        "purchasing",
        Map.of());
    add(
        "create_purchase_order",
        "OC de más de $ 20.000.000: aprueba Gerencia",
        1,
        "20000000.01",
        null,
        "manager",
        Map.of());
    add(
        "reassign_purchase_order",
        "Reasignación con sobrecosto ≤ 10 %: se ejecuta y se notifica",
        2,
        null,
        null,
        null,
        Map.of("overcost_pct_lte", 10));
    add(
        "reassign_purchase_order",
        "Reasignación con sobrecosto > 10 %: aprueba Compras",
        1,
        null,
        null,
        "purchasing",
        Map.of("overcost_pct_gt", 10));
    add(
        "cancel_purchase_order",
        "Cancelar una OC: aprueba Compras",
        1,
        null,
        null,
        "purchasing",
        Map.of());
    add("wait_purchase_order", "Esperar una OC retrasada", 3, null, null, null, Map.of());
    add(
        "propose_stock_transfer",
        "Traslado entre bodegas: aprueba Gerencia",
        1,
        null,
        null,
        "manager",
        Map.of());
    add(
        "create_warehouse_task",
        "Tareas de bodega: se ejecutan sin notificar",
        3,
        null,
        null,
        null,
        Map.of());
    add(
        "create_shipment",
        "Guía con la transportadora elegida: se ejecuta y se notifica",
        2,
        null,
        null,
        null,
        Map.of());
    add(
        "hold_sales_order",
        "Retener un pedido anómalo: se ejecuta y se notifica",
        2,
        null,
        null,
        null,
        Map.of());
    add("raise_alert", "Alertas: sin aprobación", 3, null, null, null, Map.of());
    add(
        "rescore_supplier",
        "Recalificar proveedores tras un cambio de precio",
        3,
        null,
        null,
        null,
        Map.of());
    add(
        "suggest_promotion",
        "Promoción de producto estancado: solo sugerencia",
        0,
        null,
        null,
        null,
        Map.of());
    add(
        "adjust_inventory",
        "Ajuste de inventario por conteo: aprueba Gerencia",
        1,
        null,
        null,
        "manager",
        Map.of());
    add(
        "apply_budget_plan",
        "Priorización por presupuesto: aprueba Gerencia",
        1,
        null,
        null,
        "manager",
        Map.of());
    add(
        "update_reorder_params",
        "Parámetros de reorden diarios: se ejecutan y se notifican",
        2,
        null,
        null,
        null,
        Map.of());
    add("send_daily_summary", "Resumen diario a gerencia y compras", 3, null, null, null, Map.of());
  }

  private void add(
      String action,
      String description,
      int level,
      String min,
      String max,
      String role,
      Map<String, Object> conditions) {
    AutonomyPolicy p = new AutonomyPolicy();
    p.setActionType(action);
    p.setDescription(description);
    p.setLevel(level);
    p.setMinAmount(min == null ? null : new BigDecimal(min));
    p.setMaxAmount(max == null ? null : new BigDecimal(max));
    p.setApproverRole(role);
    p.setConditions(new java.util.LinkedHashMap<>(conditions));
    p.setEnabled(true);
    policies.save(p);
  }
}
