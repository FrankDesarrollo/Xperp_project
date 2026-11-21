import { http, HttpResponse } from 'msw';
import type { components } from '$lib/api/schema';
import { API_URL } from '$lib/api/client';
import { decision } from './platform';

type S = components['schemas'];

// Recomendaciones tomadas de la demo real (día 2 de la simulación, semilla 42).
const recommendations: S['StockRecommendation'][] = [
	{
		product_id: 35,
		sku: 'CPE-006',
		product_name: 'Pañales etapa 3 x 30',
		warehouse_id: 2,
		warehouse_code: 'BOG-01',
		status: 'stockout',
		kind: 'transfer',
		priority: 'high',
		quantity: 72,
		days_of_cover: 0,
		available: 0,
		in_transit: 0,
		reorder_point: 9,
		lead_time_days: 2,
		source_warehouse_code: 'CAL-01',
		title: 'Trasladar 72 und desde CAL-01',
		reason:
			'CPE-006 Pañales etapa 3 x 30 en BOG-01: 0 und disponibles (0,0 días) frente a un punto de reorden de 9. CAL-01 tiene 41,7 días de cobertura: trasladar 72 und cubre 12 días aquí sin comprar ni comprometer presupuesto.'
	},
	{
		product_id: 7,
		sku: 'ABA-007',
		product_name: 'Aceite vegetal 1 L',
		warehouse_id: 1,
		warehouse_code: 'CAL-01',
		status: 'low',
		kind: 'buy',
		priority: 'medium',
		quantity: 1284,
		days_of_cover: 2.2,
		available: 260,
		in_transit: 0,
		reorder_point: 350,
		supplier_code: 'PRV-01',
		supplier_name: 'Granos del Valle S.A.S.',
		lead_time_days: 2,
		amount: '10015200.00',
		title: 'Comprar 1.284 und a Granos del Valle S.A.S.',
		reason:
			'ABA-007 Aceite vegetal 1 L en CAL-01: 260 und disponibles (2,2 días) frente a un punto de reorden de 350. Pedir 1.284 und por $ 10.015.200 cubre 12 días (pronóstico de 1.456 und), con entrega en 2 días. Granos del Valle S.A.S. tiene el mejor puntaje (0,77 frente a 0,61 de Molinera Andina S.A.S.).'
	},
	{
		product_id: 35,
		sku: 'CPE-006',
		product_name: 'Pañales etapa 3 x 30',
		warehouse_id: 1,
		warehouse_code: 'CAL-01',
		status: 'over',
		kind: 'excess',
		priority: 'low',
		quantity: 165,
		days_of_cover: 41.7,
		available: 589,
		in_transit: 0,
		reorder_point: 55,
		title: 'No reponer: sobran 165 und',
		reason:
			'CPE-006 Pañales etapa 3 x 30 en CAL-01 tiene 589 und disponibles, 165 por encima del tope de 424 (30 días de demanda). La IA no lo repondrá hasta que baje del punto de reorden.'
	}
];

function answer(question: string): string {
	const q = question.toLowerCase();
	if (q.includes('recomien') || q.includes('reponer')) {
		return [
			'Esto recomiendo hoy, de lo más urgente a lo menos:',
			...recommendations
				.filter((r) => r.kind !== 'excess')
				.map((r) => `• ${r.sku} ${r.product_name} en ${r.warehouse_code}: ${r.title}.`),
			'El porqué de cada una está en Inventario → Recomendaciones de la IA.'
		].join('\n');
	}
	return 'Puedo responder preguntas de solo lectura sobre la operación. Por ejemplo: «¿qué me recomiendas reponer?».';
}

// Bandeja con una OC y una guía por aprobar; la aprobación en lote las resuelve en memoria.
let approvals: S['Approval'][] = [
	{
		id: 1,
		ai_decision_id: 1,
		requested_role: 'purchasing',
		status: 'pending',
		created_at: '2026-11-01T06:00:00-05:00',
		expires_at: '2026-11-02T06:00:00-05:00',
		decision
	},
	{
		id: 2,
		ai_decision_id: 2,
		requested_role: 'manager',
		status: 'pending',
		created_at: '2026-11-01T11:00:00-05:00',
		expires_at: '2026-11-02T11:00:00-05:00',
		decision: {
			...decision,
			id: 2,
			decision_type: 'carrier_selection',
			action_type: 'create_shipment',
			title: 'Despachar PV-2026-000041 con TRP-01',
			trigger_event: 'sales_order_packed',
			trigger_kind: 'event',
			amount: '24300.00',
			impact: [
				{ label: 'Flete', value: '24300.00', unit: 'cop', effect: 'cost' },
				{ label: 'Del valor del pedido', value: '6.1', unit: 'pct', effect: 'info' }
			],
			reasoning: 'PV-2026-000041 a Palmira (14,0 kg): Rápido Valle Logística gana con 0,92 puntos.',
			references: []
		}
	}
];

const conditionReview: S['FinancialReview'] = {
	id: 90,
	sim_time: '2026-11-22T00:00:00-05:00',
	trigger_event: 'financial_condition',
	trigger_kind: 'condition',
	period_from: '2026-11-01',
	period_to: '2026-11-22',
	summary:
		'Cuidado personal se proyecta al 108,9 % del presupuesto: faltarían $ 6.230.000 al cierre. Gerencia tiene que decidir si amplía el presupuesto o aplaza la reposición de lo que tiene más cobertura.',
	findings: [
		{
			code: 'budget_overrun_projected',
			severity: 'medium',
			title: 'Presupuesto de Cuidado personal: se proyecta al 108,9 % al cierre del mes',
			detail: 'Lleva 87,6 % ($ 61.300.000 de $ 70.000.000) el día 22 de 30.',
			recommendation:
				'Prioriza las OC de Cuidado personal por margen y días de cobertura, y decide si se amplía el presupuesto o se aplaza la reposición de lo que tiene más cobertura.',
			impact: '6230000.00',
			link: '/finanzas'
		}
	],
	llm_used: false
};
const reviews: S['FinancialReview'][] = [conditionReview];

export const aiHandlers = [
	http.get(`${API_URL}/approvals`, ({ request }) => {
		const params = new URL(request.url).searchParams;
		const status = params.get('status');
		const type = params.get('decision_type');
		const items = approvals.filter(
			(a) => (!status || a.status === status) && (!type || a.decision.decision_type === type)
		);
		return HttpResponse.json<S['ApprovalPage']>({ items, total: items.length, page: 1, size: 25 });
	}),
	http.post(`${API_URL}/approvals/batch-approve`, async ({ request }) => {
		const body = (await request.json()) as S['ApprovalBatchRequest'];
		const approved = approvals.filter((a) => body.ids.includes(a.id) && a.status === 'pending');
		approvals = approvals.map((a) =>
			approved.includes(a) ? { ...a, status: 'approved', comment: body.comment ?? null } : a
		);
		const amount = approved.reduce((sum, a) => sum + Number(a.decision.amount ?? 0), 0);
		return HttpResponse.json<S['ApprovalBatchResult']>({
			approved: approved.map((a) => a.id),
			failed: [],
			approved_amount: amount.toFixed(2)
		});
	}),
	http.get(`${API_URL}/ai/usage`, () =>
		HttpResponse.json<S['AiUsage']>({
			from: '2026-11-01',
			to: '2026-11-22',
			decisions_total: 3184,
			by_trigger: { event: 2905, condition: 61, request: 4, schedule: 214 },
			decided_by_rules: 3151,
			decided_with_llm: 33,
			llm_calls: 0,
			llm_limit_per_day: 30,
			tokens_in: 0,
			tokens_out: 0,
			estimated_cost_usd: null,
			assistant_questions: 12,
			autonomous_financial_actions: 0,
			by_day: []
		})
	),
	http.get(`${API_URL}/ai/financial-reviews`, () =>
		HttpResponse.json<S['FinancialReviewPage']>({
			items: reviews,
			total: reviews.length,
			page: 1,
			size: 6
		})
	),
	http.post(`${API_URL}/ai/financial-reviews`, () => {
		const review: S['FinancialReview'] = {
			...conditionReview,
			id: 100 + reviews.length,
			trigger_event: 'user_request',
			trigger_kind: 'request'
		};
		reviews.unshift(review);
		return HttpResponse.json(review, { status: 201 });
	}),
	http.get(`${API_URL}/ai/recommendations`, ({ request }) => {
		const params = new URL(request.url).searchParams;
		const product = Number(params.get('product_id')) || null;
		const warehouse = Number(params.get('warehouse_id')) || null;
		return HttpResponse.json(
			recommendations.filter(
				(r) =>
					(product === null || r.product_id === product) &&
					(warehouse === null || r.warehouse_id === warehouse)
			)
		);
	}),
	http.post(`${API_URL}/assistant/messages`, async ({ request }) => {
		const body = (await request.json()) as S['AssistantMessageRequest'];
		return HttpResponse.json<S['AssistantReply']>({
			conversation_id: body.conversation_id ?? 'mock-1',
			message: {
				id: Date.now(),
				role: 'assistant',
				content: answer(body.content),
				tool_calls: [{ tool: 'list_recommendations', args: {} }],
				created_at: '2026-11-03T09:30:00-05:00'
			}
		});
	}),
	http.get(`${API_URL}/warehouses`, () =>
		HttpResponse.json<S['Warehouse'][]>([
			{ id: 1, code: 'CAL-01', name: 'Bodega principal Acopi', city: 'Yumbo' },
			{ id: 2, code: 'BOG-01', name: 'Bodega satélite Bogotá', city: 'Bogotá' }
		])
	),
	http.get(`${API_URL}/categories`, () => HttpResponse.json<S['Category'][]>([]))
];
