# Eventos

Este archivo cataloga los eventos de dominio que se envían los módulos del backend entre sí y los eventos SSE que el backend envía al frontend. La referencia está en `AGENTS.md`, secciones 7.5 y 7.6. Si se agrega un evento o cambia su forma, se documenta aquí en el mismo PR.

## Eventos de dominio

Se publican con `ApplicationEventPublisher` y se consumen con `@ApplicationModuleListener` de Spring Modulith. El consumo es asíncrono, ocurre después del commit y el evento queda persistido en `event_publication`. Cada evento es un `record` dentro del paquete `events/` del módulo que lo publica.

| Evento | Lo publica | Lo consumen | Campos |
|---|---|---|---|
| `SalesOrderCreated` | sales | inventory, ai | _por definir_ |
| `StockReserved` / `StockReleased` | inventory | dashboard | _por definir_ |
| `StockBelowSafety` | inventory | ai (revisión urgente) | _por definir_ |
| `InventoryCountRecorded` | inventory | ai (discrepancias) | _por definir_ |
| `PurchaseOrderSent` | purchasing | finance, notifications | _por definir_ |
| `PurchaseOrderStatusChanged` | partners | purchasing, ai, notifications | _por definir_ |
| `SupplierPriceChanged` | partners | purchasing, ai | _por definir_ |
| `GoodsReceived` | purchasing | inventory, finance | _por definir_ |
| `WarehouseTaskCompleted` | logistics | sales, ai | _por definir_ |
| `SalesOrderPacked` | logistics | ai (transportadora) | _por definir_ |
| `ShipmentStatusChanged` | partners | logistics, sales, notifications | _por definir_ |
| `AiDecisionRecorded` | ai | notifications | _por definir_ |
| `ApprovalRequested` / `ApprovalResolved` | ai | notifications, ai | _por definir_ |
| `SimulationDayStarted` / `SimulationDayEnded` | simulation | ai, dashboard | _por definir_ |

## Eventos SSE

`GET /api/v1/stream` envía cada evento con un nombre en `event:` y un JSON en `data:`. El frontend se conecta con `fetch` y manda el token en el encabezado. No usa `EventSource` porque no permite encabezados.

| Evento | Cuándo | `data` |
|---|---|---|
| `clock.tick` | Cada hora simulada | _por definir_ |
| `sale.created` | Nuevo pedido de venta | _por definir_ |
| `inventory.changed` | Cambia el stock de un SKU en una bodega | _por definir_ |
| `purchase_order.changed` | Cambia el estado de una OC | _por definir_ |
| `task.created` / `task.changed` | Se crea o cambia una tarea de bodega | _por definir_ |
| `shipment.changed` | Cambia el estado de un envío | _por definir_ |
| `ai.decision` | Se registra una decisión de la IA | _por definir_ |
| `approval.requested` | Se pide una aprobación | _por definir_ |
| `alert.created` | Nueva alerta | _por definir_ |
| `notification.created` | Nueva notificación para el usuario | _por definir_ |
