package com.xperp.ai.decisions;

import java.util.Map;

/**
 * Qué activó cada análisis de la IA (ADR 0004). La IA no corre todo el tiempo: se activa cuando
 * algo pasa, cuando se cruza un umbral, cuando una persona lo pide o en una revisión programada.
 */
public final class TriggerKind {

  /** Algo pasó: un pedido, un webhook de un proveedor, un conteo, un cambio de precio. */
  public static final String EVENT = "event";

  /** Se cruzó un umbral: stock bajo el de seguridad, presupuesto proyectado por encima. */
  public static final String CONDITION = "condition";

  /** Lo pidió una persona. */
  public static final String REQUEST = "request";

  /** Revisión programada: ciclo diario, cierre del día, revisión semanal o de mes. */
  public static final String SCHEDULE = "schedule";

  private static final Map<String, String> KINDS =
      Map.ofEntries(
          Map.entry("sales_order_created", EVENT),
          Map.entry("sales_order_released", EVENT),
          Map.entry("sales_order_packed", EVENT),
          Map.entry("supplier_price_changed", EVENT),
          Map.entry("price_anomaly", EVENT),
          Map.entry("inventory_count_recorded", EVENT),
          Map.entry("inventory_discrepancy", EVENT),
          // El LLM solo llama tools de escritura dentro de un agente que atiende un evento.
          Map.entry("llm_tool", EVENT),
          Map.entry("stock_below_safety", CONDITION),
          Map.entry("budget_prioritization", CONDITION),
          Map.entry("financial_condition", CONDITION),
          Map.entry("daily_cycle", SCHEDULE),
          Map.entry("simulation_day_started", SCHEDULE),
          Map.entry("simulation_day_ended", SCHEDULE),
          Map.entry("financial_review_weekly", SCHEDULE),
          Map.entry("month_close", SCHEDULE),
          Map.entry("user_request", REQUEST),
          Map.entry("manual", REQUEST));

  private TriggerKind() {}

  public static String of(String triggerEvent) {
    if (triggerEvent != null && triggerEvent.startsWith("purchase_order_")) {
      return EVENT; // purchase_order_rejected, purchase_order_delayed...
    }
    return KINDS.getOrDefault(triggerEvent, EVENT);
  }

  /** ¿Está clasificado a propósito? Lo usa la prueba que revisa los disparadores del código. */
  static boolean known(String triggerEvent) {
    return triggerEvent != null
        && (triggerEvent.startsWith("purchase_order_") || KINDS.containsKey(triggerEvent));
  }
}
