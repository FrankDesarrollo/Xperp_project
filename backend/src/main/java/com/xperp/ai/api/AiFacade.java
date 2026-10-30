package com.xperp.ai.api;

import java.time.LocalDate;

/**
 * Lo único que otros módulos ven de la capa de IA. La simulación llama estos métodos según el reloj
 * simulado (AGENTS.md, 10.1): pronóstico a las 05:00, ciclo de reabastecimiento a las 06:00 y
 * resumen al cierre del día.
 */
public interface AiFacade {

  /** Políticas de autonomía y ajustes por defecto de una empresa nueva. */
  void initializeCompany();

  /** Parámetros de inventario a partir del historial (carga demo, paso 3). */
  void initializeReorderParams(LocalDate today);

  /** Inicio del día: parámetros de reorden y productos estancados. */
  void onDayStarted(LocalDate day);

  /** 05:00: pronóstico de demanda a 30 días. */
  void runForecast(LocalDate day);

  /** 05:30: lee los precios vigentes de los proveedores. */
  void syncSupplierPrices();

  /** 06:00: ciclo diario de reabastecimiento. */
  void runReplenishment(LocalDate day);

  /** Fin del día: resumen para gerencia y compras. */
  void onDayEnded(LocalDate day);

  /** Cada hora simulada: vence aprobaciones y reintenta lo pendiente. */
  void hourly(boolean autoApprove);

  /** active, shadow o stopped. */
  String aiMode();
}
