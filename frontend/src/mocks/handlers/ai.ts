import { http, HttpResponse } from 'msw';
import type { components } from '$lib/api/schema';
import { API_URL } from '$lib/api/client';

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

export const aiHandlers = [
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
