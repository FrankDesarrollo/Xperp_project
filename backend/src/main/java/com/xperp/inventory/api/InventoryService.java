package com.xperp.inventory.api;

import com.xperp.shared.persistence.PageResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** API pública de inventario: bodegas, existencias, kardex, conteos y traslados. */
public interface InventoryService {

  // Bodegas

  List<WarehouseDto> warehouses();

  WarehouseDto warehouse(long id);

  Optional<WarehouseDto> warehouseByCode(String code);

  WarehouseDto createWarehouse(String code, String name, String city, String address);

  // Existencias

  StockDto stock(long productId, long warehouseId);

  List<StockDto> allStock();

  /** Existencias con datos del producto, en tránsito y semáforo (pantalla de inventario). */
  List<InventoryRow> rows(Long warehouseId);

  InventoryRow row(long productId, long warehouseId);

  /** Fija el inventario inicial (carga demo): crea la fila y un movimiento de ajuste. */
  void setInitialStock(long productId, long warehouseId, int quantity, BigDecimal unitCost);

  /** Reserva hasta {@code quantity} unidades disponibles; devuelve cuántas reservó. */
  int reserve(long productId, long warehouseId, int quantity);

  void releaseReservation(long productId, long warehouseId, int quantity);

  /** Salida por despacho: bajan {@code on_hand} y {@code reserved}. */
  void dispatch(
      long productId, long warehouseId, int quantity, String referenceType, long referenceId);

  /** Entrada por recepción de compra. */
  void receive(
      long productId,
      long warehouseId,
      int quantity,
      BigDecimal unitCost,
      String referenceType,
      long referenceId,
      String receivedBy);

  MovementDto adjust(
      long productId, long warehouseId, int delta, String reason, String referenceType, Long refId);

  void recordLostSale(long productId, long warehouseId, Long salesOrderId, int quantity);

  PageResult<MovementDto> movements(long productId, long warehouseId, Integer page, Integer size);

  // Parámetros que calcula la IA

  void updateParams(long productId, long warehouseId, StockParams params);

  void setFlags(long productId, long warehouseId, boolean promotionSuggested, boolean blocked);

  // Conteos

  CountDto recordCount(long productId, long warehouseId, int countedQty, boolean recount);

  CountDto count(long id);

  List<CountDto> recentCounts(Long warehouseId);

  void markCountAdjusted(long countId);

  // Traslados

  TransferDto createTransfer(
      long fromWarehouseId, long toWarehouseId, List<TransferLine> lines, Long aiDecisionId);

  TransferDto transfer(long id);

  PageResult<TransferDto> transfers(String status, Integer page, Integer size);

  TransferDto shipTransfer(long id);

  TransferDto receiveTransfer(long id);

  TransferDto cancelTransfer(long id);
}
