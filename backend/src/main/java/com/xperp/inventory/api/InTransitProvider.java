package com.xperp.inventory.api;

import java.util.Map;

/**
 * Unidades que vienen en camino desde fuera del módulo (OC abiertas). La implementa compras, que
 * depende de inventario y no al revés. Ver ADR 0003.
 */
public interface InTransitProvider {

  Map<StockKey, Integer> inboundUnits();
}
