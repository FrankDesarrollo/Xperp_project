import { http, HttpResponse } from 'msw';
import type { components } from '$lib/api/schema';
import { API_URL } from '$lib/api/client';

type S = components['schemas'];

const clock: S['SimulationClock'] = {
	sim_time: '2026-11-03T09:30:00-05:00',
	status: 'paused',
	seconds_per_day: 20,
	day_number: 3,
	scenario_cursor: 4,
	scenarios_total: 14,
	ai_mode: 'active'
};

// Desde el ADR 0004 una OC siempre espera a una persona, con su impacto calculado por el backend.
export const decision: S['AiDecision'] = {
	id: 1,
	decision_type: 'replenishment',
	action_type: 'create_purchase_order',
	title: 'Reabastecer BEB-001 en CAL-01 con PRV-03',
	trigger_event: 'daily_cycle',
	trigger_kind: 'schedule',
	input_snapshot: { bodega: 'CAL-01', disponible: 400, punto_reorden: 562 },
	alternatives: [
		{ proveedor: 'PRV-03', puntaje: 0.86 },
		{ proveedor: 'PRV-09', puntaje: 0.61 }
	],
	proposed_action: { supplier_code: 'PRV-03', total: '3024000.00' },
	reasoning:
		'Leche entera UHT 1 L: disponible 400 und (2,0 días) frente a un punto de reorden de 562; se piden 864 und. Lácteos La Pradera ganó con 0,86 puntos.',
	confidence: 0.95,
	impact: [
		{ label: 'Compromete', value: '3024000.00', unit: 'cop', effect: 'cost' },
		{
			label: 'Usa del disponible de Lácteos y bebidas',
			value: '3.4',
			unit: 'pct',
			effect: 'info',
			note: 'Disponible antes de esta OC: $ 90.000.000'
		},
		{
			label: 'Margen bruto esperado al venderlo',
			value: '777600.00',
			unit: 'cop',
			effect: 'benefit'
		},
		{
			label: 'Ventas en riesgo si no se compra hoy',
			value: '880000.00',
			unit: 'cop',
			effect: 'risk',
			note: 'Estimado: 200 und con la demanda promedio'
		},
		{
			label: 'Cobertura al recibir',
			value: '4.3',
			unit: 'days',
			effect: 'benefit',
			note: 'BEB-001: hoy alcanza 2 días'
		}
	],
	amount: '3024000.00',
	policy_result: 'pending_approval',
	policy_reason:
		'Mueve dinero o activos: la aprueba una persona antes de ejecutarse. OC de menos de $ 5.000.000: aprueba Compras',
	autonomy_level: 1,
	status: 'pending_approval',
	result_message: 'Esperando aprobación de Compras (#1)',
	reversible: true,
	llm_used: false,
	sim_time: '2026-11-01T06:00:00-05:00',
	created_at: '2026-11-01T06:00:00-05:00',
	references: [{ type: 'purchase_order', id: 3, label: 'OC-2026-000003' }]
};

const kpis: S['Kpis'] = {
	from: '2026-11-01',
	to: '2026-11-03',
	sales_today: '12500000.00',
	sales_period: '61200000.00',
	orders_today: 42,
	gross_margin: '11100000.00',
	gross_margin_pct: 18.2,
	service_level: 0.991,
	lost_sales_units: 35,
	lost_sales_amount: '210000.00',
	stockout_skus: 1,
	low_stock_skus: 6,
	open_purchase_orders: 4,
	late_purchase_orders: 0,
	purchases_period: '28800000.00',
	budget_execution_pct: 4.8,
	inventory_value: '201900000.00',
	rotation: 0.3,
	decisions: {
		executed: 120,
		pending_approval: 2,
		suggested: 1,
		blocked: 0,
		approved: 3,
		rejected: 0
	},
	avg_approval_minutes: 240,
	overcost_assumed: '0.00',
	inventory_days_by_category: [],
	supplier_fulfillment: []
};

// Mes de la demo hasta el día 22: Cuidado personal se proyecta por encima del presupuesto.
const finance: S['FinanceOverview'] = {
	from: '2026-11-01',
	to: '2026-11-22',
	days_elapsed: 22,
	days_in_period: 30,
	revenue: '612400000.00',
	cost_of_sales: '478300000.00',
	gross_margin: '134100000.00',
	gross_margin_pct: 21.9,
	freight: '38200000.00',
	freight_pct: 6.2,
	margin_after_freight: '95900000.00',
	purchases_received: '401800000.00',
	purchases_committed: '62300000.00',
	lost_sales_amount: '9800000.00',
	payables_open: '318500000.00',
	payables_due_7d: '41200000.00',
	payables_overdue: '0.00',
	inventory_value: '486900000.00',
	inventory_excess_value: '6100000.00',
	slow_mover_value: '17900000.00',
	budgets: [
		{
			category_id: 1,
			category_code: 'ABA',
			category_name: 'Abarrotes y granos',
			amount: '260000000.00',
			committed: '21000000.00',
			spent: '158000000.00',
			used_pct: 68.8,
			projected_pct: 92.4,
			projected_overrun: '0.00'
		},
		{
			category_id: 4,
			category_code: 'CPE',
			category_name: 'Cuidado personal',
			amount: '70000000.00',
			committed: '6200000.00',
			spent: '55100000.00',
			used_pct: 87.6,
			projected_pct: 108.9,
			projected_overrun: '6230000.00'
		}
	]
};

export const platformHandlers = [
	http.get(`${API_URL}/simulation/clock`, () => HttpResponse.json(clock)),
	http.get(`${API_URL}/simulation/scenarios`, () => HttpResponse.json([])),
	http.get(`${API_URL}/notifications`, () =>
		HttpResponse.json<S['NotificationPage']>({ items: [], total: 0, page: 1, size: 30, unread: 0 })
	),
	http.get(`${API_URL}/stream`, () => new HttpResponse(null, { status: 204 })),
	http.get(`${API_URL}/dashboard/kpis`, () => HttpResponse.json(kpis)),
	http.get(`${API_URL}/dashboard/series`, ({ request }) =>
		HttpResponse.json<S['Series']>({
			metric: new URL(request.url).searchParams.get('metric') ?? 'sales',
			points: [
				{ date: '2026-11-01', value: 20500000 },
				{ date: '2026-11-02', value: 9100000 },
				{ date: '2026-11-03', value: 12500000 }
			]
		})
	),
	http.get(`${API_URL}/budgets`, () =>
		HttpResponse.json<S['Budget'][]>([
			{
				id: 1,
				category_id: 1,
				category_code: 'ABA',
				category_name: 'Abarrotes y granos',
				period: '2026-11',
				amount: '260000000.00',
				committed: '8900000.00',
				spent: '0.00',
				available: '251100000.00',
				usage_pct: 3.4
			}
		])
	),
	http.get(`${API_URL}/inventory`, () =>
		HttpResponse.json<S['InventoryPage']>({ items: [], total: 0, page: 1, size: 20 })
	),
	http.get(`${API_URL}/ai/decisions`, () =>
		HttpResponse.json<S['AiDecisionPage']>({ items: [decision], total: 1, page: 1, size: 20 })
	),
	http.get(`${API_URL}/ai/alerts`, () => HttpResponse.json<S['Alert'][]>([])),
	http.get(`${API_URL}/dashboard/finance`, () => HttpResponse.json(finance))
];
