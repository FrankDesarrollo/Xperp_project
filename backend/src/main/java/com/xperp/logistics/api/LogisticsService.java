package com.xperp.logistics.api;

import com.xperp.shared.persistence.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** API pública de logística: transportadoras, tareas de bodega y envíos. */
public interface LogisticsService {

  CarrierDto createCarrier(CarrierCommand command);

  List<CarrierDto> carriers();

  CarrierDto carrier(long id);

  // Tareas

  TaskDto createTask(CreateTask command);

  /** Alistamiento y empaque de un pedido, asignados al bodeguero de la bodega. */
  List<TaskDto> createFulfillmentTasks(long salesOrderId, Long aiDecisionId);

  void cancelTask(long id);

  void cancelOpenTasksForOrder(long salesOrderId);

  TaskDto task(long id);

  List<TaskLineDto> taskLines(TaskDto task);

  PageResult<TaskDto> tasks(
      Long assignedTo, String status, String type, Long warehouseId, Integer page, Integer size);

  TaskDto start(long id, boolean byHuman);

  TaskDto complete(long id, TaskCompletion completion, boolean byHuman);

  /** Tareas vencidas que puede tomar el bodeguero virtual. */
  List<TaskDto> dueForVirtualWorker(Instant now, int limit);

  // Envíos

  ShipmentContext shipmentContext(long salesOrderId);

  /** Cotiza con las transportadoras que cubren la ciudad del cliente. */
  List<CarrierQuoteDto> quote(long salesOrderId);

  /** Crea el envío, genera la guía y registra la salida en el kardex. */
  ShipmentDto createShipment(
      long salesOrderId,
      long carrierId,
      String service,
      BigDecimal cost,
      int etaDays,
      Long aiDecisionId);

  ShipmentDto shipment(long id);

  boolean hasShipment(long salesOrderId);

  PageResult<ShipmentDto> shipments(String status, Long carrierId, Integer page, Integer size);

  byte[] label(long id);
}
