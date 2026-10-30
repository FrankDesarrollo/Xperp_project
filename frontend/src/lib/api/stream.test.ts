import { describe, expect, it, vi } from 'vitest';

vi.mock('$env/dynamic/public', () => ({ env: {} }));

const { parseBlock } = await import('./stream');

describe('parseBlock', () => {
	it('lee el nombre del evento y el JSON de datos', () => {
		const e = parseBlock('event:clock.tick\ndata:{"status":"running","day_number":3}');
		expect(e).toEqual({ event: 'clock.tick', data: { status: 'running', day_number: 3 } });
	});

	it('une varias líneas de datos', () => {
		const e = parseBlock('event:sale.created\ndata:{"id":1,\ndata:"number":"PV-2026-000001"}');
		expect(e?.data).toEqual({ id: 1, number: 'PV-2026-000001' });
	});

	it('ignora comentarios (latidos) y JSON inválido', () => {
		expect(parseBlock(':ping')).toBeNull();
		expect(parseBlock('event:x\ndata:no-es-json')).toBeNull();
	});
});
