# Eventos

Este archivo cataloga los eventos de dominio que se envían los módulos del backend entre sí y los eventos SSE que el backend envía al frontend. La referencia está en `AGENTS.md`, secciones 7.5 y 7.6. Si se agrega un evento o cambia su forma, se documenta aquí en el mismo PR.

## Eventos de dominio

Se publican con `ApplicationEventPublisher` y se consumen con `@ApplicationModuleListener` de Spring Modulith. El consumo es asíncrono, ocurre después del commit y el evento queda persistido en `event_publication`. Cada evento es un `record` dentro del paquete `events/` del módulo que lo publica. Todos llevan `companyId` como primer campo; el listener lo usa para fijar el tenant (ver `TenantTaskDecorator`).

| Evento | Lo publica | Lo consumen | Campos (además de `companyId`) |
|---|---|---|---|
| `SalesOrderCreated` | sales | ai (alistamiento, anomalías) | `salesOrderId`, `number`, `warehouseId`, `status`, `total`, `holdReason` |
| `SalesOrderReleased` | sales | ai (alistamiento tras liberar un pedido retenido) | `salesOrderId`, `number` |
| `SalesOrderCancelled` | sales | logistics (cancela tareas pendientes) | `salesOrderId`, `number` |
| `StockBelowSafety` | inventory | ai (revisión urgente) | `productId`, `warehouseId`, `available`, `safetyStock` |
| `InventoryCountRecorded` | inventory | ai (discrepancias) | `countId`, `productId`, `warehouseId`, `systemQty`, `countedQty`, `recount` |
| `PurchaseOrderSent` | purchasing | finance (compromete presupuesto), notifications (correo con PDF) | `purchaseOrderId`, `number`, `supplierId`, `warehouseId`, `total`, `amountsByCategory`, `period`, `createdByAi` |
| `SupplierUpdateReceived` | partners | purchasing | `externalId`, `poNumber`, `status`, `expectedAt`, `reason`, `items[{sku, quantity}]` |
| `PurchaseOrderStatusChanged` | purchasing | ai (excepciones), finance (libera compromiso), notifications | `purchaseOrderId`, `number`, `supplierId`, `warehouseId`, `previousStatus`, `status`, `reason`, `expectedAt` |
| `PurchaseOrderDelivered` | purchasing | logistics (tarea de recepción) | `purchaseOrderId`, `number`, `warehouseId` |
| `SupplierPriceChanged` | purchasing | ai (alerta y nueva calificación) | `supplierId`, `productId`, `previousCost`, `newCost`, `changePct` |
| `GoodsReceived` | purchasing | finance (gasto y cuenta por pagar) | `purchaseOrderId`, `number`, `supplierId`, `paymentTermsDays`, `total`, `amountsByCategory` |
| `WarehouseTaskCreated` | logistics | notifications (aviso al bodeguero) | `taskId`, `type`, `warehouseId`, `title`, `assignedToUserId` |
| `WarehouseTaskCompleted` | logistics | (disponible; logistics llama a sales e inventory por servicio al completar) | `taskId`, `type`, `warehouseId`, `salesOrderId`, `purchaseOrderId`, `stockTransferId`, `byHuman` |
| `SalesOrderPacked` | logistics | ai (transportadora) | `salesOrderId`, `warehouseId` |
| `CarrierUpdateReceived` | partners | logistics | `trackingNumber`, `status`, `occurredAt` |
| `ShipmentStatusChanged` | logistics | (disponible; el estado del pedido se actualiza por servicio) | `shipmentId`, `salesOrderId`, `trackingNumber`, `status` |
| `AiDecisionRecorded` | ai | notifications | `decisionId`, `decisionType`, `actionType`, `title`, `policyResult`, `status`, `notifyUsers`, `summary` |
| `ApprovalRequested` | ai | notifications | `approvalId`, `decisionId`, `requestedRole`, `title`, `amount` |
| `ApprovalResolved` | ai | (disponible) | `approvalId`, `decisionId`, `status`, `title`, `decidedBy` |
| `AlertRaised` | ai | notifications | `alertId`, `type`, `severity`, `message` |
| `SimulationDayStarted` / `SimulationDayEnded` | simulation | (disponible; ver ADR 0003) | `day`, `dayNumber` |

Diferencias con `AGENTS.md` 7.5, justificadas en `docs/decisions/0003-desviaciones-de-la-implementacion.md`:

- Los webhooks llegan a `partners`, que publica `SupplierUpdateReceived` y `CarrierUpdateReceived`. El dueño del dato (purchasing, logistics) aplica el cambio y publica `PurchaseOrderStatusChanged` y `ShipmentStatusChanged`.
- `StockReserved` / `StockReleased` no existen como eventos de dominio: el tablero lee el inventario directamente y el cambio sale por SSE (`inventory.changed`).
- La simulación llama a `AiFacade` en horas fijas (05:00 pronóstico, 06:00 reabastecimiento, 00:00 resumen) en vez de que la IA escuche `SimulationDay*`. Así el orden de los trabajos es determinista con la misma semilla.

## Eventos SSE

`GET /api/v1/stream` envía cada evento con un nombre en `event:` y un JSON en `data:`. El frontend se conecta con `fetch` y manda el token en el encabezado. No usa `EventSource` porque no permite encabezados. El servidor agrupa los eventos cada 500 ms (si hay varios con la misma clave, solo sale el último) y manda un comentario `:ping` cada 15 s.

| Evento | Cuándo | `data` |
|---|---|---|
| `clock.tick` | Cada hora simulada y al cambiar de estado | `sim_time`, `status`, `seconds_per_day`, `day_number`, `ai_mode` |
| `sale.created` | Nuevo pedido de venta | `id`, `number`, `customer_name`, `warehouse_id`, `status`, `total`, `ordered_at` |
| `inventory.changed` | Cambia el stock de un SKU en una bodega | `product_id`, `warehouse_id`, `on_hand`, `reserved`, `available` |
| `purchase_order.changed` | Cambia el estado de una OC | `id`, `number`, `status`, … |
| `task.created` / `task.changed` | Se crea o cambia una tarea de bodega | Tarea completa, igual que `WarehouseTask` del contrato |
| `shipment.changed` | Cambia el estado de un envío | `id`, `tracking_number`, `status`, … |
| `ai.decision` | Se registra una decisión de la IA | `id`, `decision_type`, `title`, `status`, `policy_result` |
| `approval.requested` | Se pide una aprobación | `id`, `ai_decision_id`, `requested_role`, `title` |
| `alert.created` | Nueva alerta | `id`, `type`, `severity`, `message` |
| `notification.created` | Nueva notificación para el usuario (solo a ese usuario) | `id`, `type`, `title`, `body`, `link`, `created_at` |
