/**
 * Conexión SSE con `GET /api/v1/stream` (AGENTS.md, sección 7.6).
 *
 * Usa `fetch` con el token en el encabezado (no `EventSource`, que no permite encabezados) y se
 * reconecta sola si se cae. Los nombres de evento están en `contracts/events.md`.
 */

export type StreamEventName =
	| 'clock.tick'
	| 'sale.created'
	| 'inventory.changed'
	| 'purchase_order.changed'
	| 'task.created'
	| 'task.changed'
	| 'shipment.changed'
	| 'ai.decision'
	| 'approval.requested'
	| 'alert.created'
	| 'notification.created';

export interface StreamEvent<T = unknown> {
	event: StreamEventName;
	data: T;
}

// Pendiente (fase 6): connectStream(onEvent) con reconexión exponencial.
