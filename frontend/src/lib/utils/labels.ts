/** Etiquetas en español para estados, tipos y roles que llegan en inglés desde la API. */

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

export const ROLE_LABEL: Record<string, string> = {
	admin: 'Administración',
	manager: 'Gerencia',
	purchasing: 'Compras',
	warehouse: 'Bodega',
	viewer: 'Consulta'
};

export const STOCK_STATUS: Record<string, { label: string; tone: Tone; icon: string }> = {
	stockout: { label: 'Quiebre', tone: 'danger', icon: '⛔' },
	low: { label: 'Bajo', tone: 'warning', icon: '▼' },
	ok: { label: 'Normal', tone: 'success', icon: '●' },
	over: { label: 'Exceso', tone: 'info', icon: '▲' }
};

export const SALES_STATUS: Record<string, { label: string; tone: Tone }> = {
	created: { label: 'Creado', tone: 'info' },
	on_hold: { label: 'Retenido', tone: 'warning' },
	picking: { label: 'Alistando', tone: 'info' },
	packed: { label: 'Empacado', tone: 'info' },
	shipped: { label: 'Despachado', tone: 'success' },
	delivered: { label: 'Entregado', tone: 'success' },
	cancelled: { label: 'Cancelado', tone: 'neutral' }
};

export const PO_STATUS: Record<string, { label: string; tone: Tone }> = {
	draft: { label: 'Borrador', tone: 'neutral' },
	pending_approval: { label: 'Por aprobar', tone: 'warning' },
	sent: { label: 'Enviada', tone: 'info' },
	confirmed: { label: 'Confirmada', tone: 'info' },
	delayed: { label: 'Retrasada', tone: 'warning' },
	rejected: { label: 'Rechazada', tone: 'danger' },
	shipped: { label: 'En camino', tone: 'info' },
	delivered: { label: 'Entregada', tone: 'success' },
	received: { label: 'Recibida', tone: 'success' },
	cancelled: { label: 'Cancelada', tone: 'neutral' }
};

export const TASK_TYPE: Record<string, string> = {
	pick: 'Alistamiento',
	pack: 'Empaque',
	receive: 'Recepción',
	count: 'Conteo',
	transfer_out: 'Salida de traslado',
	transfer_in: 'Entrada de traslado'
};

export const TASK_STATUS: Record<string, { label: string; tone: Tone }> = {
	pending: { label: 'Pendiente', tone: 'warning' },
	in_progress: { label: 'En curso', tone: 'info' },
	done: { label: 'Terminada', tone: 'success' },
	cancelled: { label: 'Cancelada', tone: 'neutral' }
};

export const SHIPMENT_STATUS: Record<string, { label: string; tone: Tone }> = {
	created: { label: 'Guía creada', tone: 'info' },
	in_transit: { label: 'En tránsito', tone: 'info' },
	delivered: { label: 'Entregado', tone: 'success' },
	failed: { label: 'Fallido', tone: 'danger' }
};

export const TRANSFER_STATUS: Record<string, { label: string; tone: Tone }> = {
	requested: { label: 'Solicitado', tone: 'warning' },
	in_transit: { label: 'En camino', tone: 'info' },
	received: { label: 'Recibido', tone: 'success' },
	cancelled: { label: 'Cancelado', tone: 'neutral' }
};

export const DECISION_TYPE: Record<string, string> = {
	replenishment: 'Reabastecimiento',
	po_exception: 'Excepción de OC',
	stock_transfer: 'Traslado',
	fulfillment: 'Alistamiento',
	carrier_selection: 'Transportadora',
	demand_anomaly: 'Anomalía de venta',
	price_anomaly: 'Anomalía de precio',
	inventory_discrepancy: 'Discrepancia de conteo',
	slow_mover: 'Producto estancado',
	budget_prioritization: 'Priorización por presupuesto',
	reorder_params_update: 'Parámetros de reorden',
	daily_summary: 'Resumen diario',
	assistant_answer: 'Asistente'
};

export const DECISION_STATUS: Record<string, { label: string; tone: Tone }> = {
	executing: { label: 'Ejecutando', tone: 'info' },
	executed: { label: 'Ejecutada', tone: 'success' },
	pending_approval: { label: 'Por aprobar', tone: 'warning' },
	approved: { label: 'Aprobada', tone: 'success' },
	rejected: { label: 'Rechazada', tone: 'neutral' },
	expired: { label: 'Vencida', tone: 'neutral' },
	suggested: { label: 'Sugerencia', tone: 'info' },
	blocked: { label: 'Bloqueada', tone: 'danger' },
	reverted: { label: 'Revertida', tone: 'neutral' },
	failed: { label: 'Falló', tone: 'danger' }
};

export const ACTION_TYPE: Record<string, string> = {
	create_purchase_order: 'Crear OC',
	reassign_purchase_order: 'Reasignar OC',
	cancel_purchase_order: 'Cancelar OC',
	wait_purchase_order: 'Esperar OC',
	propose_stock_transfer: 'Proponer traslado',
	create_warehouse_task: 'Crear tarea de bodega',
	create_shipment: 'Crear guía',
	hold_sales_order: 'Retener pedido',
	raise_alert: 'Alerta',
	rescore_supplier: 'Recalificar proveedores',
	suggest_promotion: 'Sugerir promoción',
	adjust_inventory: 'Ajustar inventario',
	apply_budget_plan: 'Plan de presupuesto',
	update_reorder_params: 'Actualizar parámetros',
	send_daily_summary: 'Enviar resumen'
};

export const LEVEL_LABEL: Record<number, string> = {
	0: 'Nivel 0 · solo sugiere',
	1: 'Nivel 1 · espera aprobación',
	2: 'Nivel 2 · ejecuta y notifica',
	3: 'Nivel 3 · ejecuta sin notificar'
};

export const AI_MODE: Record<string, { label: string; tone: Tone }> = {
	active: { label: 'IA activa', tone: 'success' },
	shadow: { label: 'Modo sombra', tone: 'warning' },
	stopped: { label: 'IA detenida', tone: 'danger' }
};

export const SEVERITY: Record<string, Tone> = {
	info: 'info',
	warning: 'warning',
	critical: 'danger'
};

export function label<T extends { label: string }>(
	map: Record<string, T>,
	key: string | null | undefined
): T {
	return (key && map[key]) || ({ label: key ?? '—', tone: 'neutral' } as unknown as T);
}

/** Qué recomienda la IA para una existencia (GET /ai/recommendations). */
export const RECOMMENDATION_KIND: Record<string, { label: string; tone: Tone }> = {
	buy: { label: 'Comprar', tone: 'warning' },
	transfer: { label: 'Trasladar', tone: 'info' },
	incoming: { label: 'OC en camino', tone: 'info' },
	pending_approval: { label: 'Por aprobar', tone: 'warning' },
	excess: { label: 'Exceso', tone: 'neutral' }
};

export const RECOMMENDATION_PRIORITY: Record<string, { label: string; tone: Tone }> = {
	high: { label: 'Urgente', tone: 'danger' },
	medium: { label: 'Reponer', tone: 'warning' },
	low: { label: 'Para revisar', tone: 'neutral' }
};
