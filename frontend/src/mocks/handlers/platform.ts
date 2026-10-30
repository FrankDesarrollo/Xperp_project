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

const decision: S['AiDecision'] = {
	id: 1,
	decision_type: 'replenishment',
	action_type: 'create_purchase_order',
	title: 'Reabastecer BEB-001 en CAL-01 con PRV-03',
	trigger_event: 'daily_cycle',
	input_snapshot: { bodega: 'CAL-01', disponible: 400, punto_reorden: 562 },
	alternatives: [
		{ proveedor: 'PRV-03', puntaje: 0.86 },
		{ proveedor: 'PRV-09', puntaje: 0.61 }
	],
	proposed_action: { supplier_code: 'PRV-03', total: '3024000.00' },
	reasoning:
		'Leche entera UHT 1 L: disponible 400 und (2,0 días) frente a un punto de reorden de 562; se piden 864 und. Lácteos La Pradera ganó con 0,86 puntos.',
	confidence: 0.95,
	amount: '3024000.00',
	policy_result: 'executed',
	policy_reason: 'OC de menos de $ 5.000.000: se ejecuta y se notifica',
	autonomy_level: 2,
	status: 'executed',
	result_message: 'Enviada OC-2026-000003 a Lácteos La Pradera S.A.S.',
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
	http.get(`${API_URL}/ai/alerts`, () => HttpResponse.json<S['Alert'][]>([]))
];
