package com.xperp.sales.api;

import java.util.List;
import java.util.Optional;

/**
 * Filtro que se aplica a cada pedido nuevo antes de reservar stock. Si devuelve un motivo, el
 * pedido queda retenido ({@code on_hold}). Lo implementa la capa de IA (anomalías de demanda), que
 * depende de ventas y no al revés.
 */
public interface SalesOrderScreener {

  Optional<String> screen(ScreeningRequest request);

  record ScreeningRequest(
      long customerId, String segmentCode, long warehouseId, List<SalesLine> lines) {}
}
