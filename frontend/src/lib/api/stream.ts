/**
 * Conexión SSE con `GET /api/v1/stream` (AGENTS.md, sección 7.6).
 *
 * Usa `fetch` con el token en el encabezado (no `EventSource`, que no permite encabezados) y se
 * reconecta sola si se cae, con espera exponencial. Los nombres de evento están en
 * `contracts/events.md`.
 */
import { API_URL } from './client';

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

export interface StreamHandle {
	close(): void;
}

/** Separa un bloque SSE en nombre de evento y datos. */
export function parseBlock(block: string): StreamEvent | null {
	let event = 'message';
	const data: string[] = [];
	for (const line of block.split('\n')) {
		if (line.startsWith('event:')) {
			event = line.slice(6).trim();
		} else if (line.startsWith('data:')) {
			data.push(line.slice(5).trimStart());
		}
	}
	if (data.length === 0) return null;
	try {
		return { event: event as StreamEventName, data: JSON.parse(data.join('\n')) };
	} catch {
		return null;
	}
}

export function connectStream(
	token: string,
	onEvent: (e: StreamEvent) => void,
	onStatus?: (connected: boolean) => void
): StreamHandle {
	let closed = false;
	let attempt = 0;
	let controller: AbortController | null = null;

	async function run() {
		while (!closed) {
			controller = new AbortController();
			try {
				const response = await fetch(`${API_URL}/stream`, {
					headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
					signal: controller.signal
				});
				if (!response.ok || !response.body) throw new Error(`SSE ${response.status}`);
				attempt = 0;
				onStatus?.(true);
				const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
				let buffer = '';
				while (!closed) {
					const { value, done } = await reader.read();
					if (done) break;
					buffer += value.replace(/\r/g, '');
					let index;
					while ((index = buffer.indexOf('\n\n')) >= 0) {
						const block = buffer.slice(0, index);
						buffer = buffer.slice(index + 2);
						const parsed = parseBlock(block);
						if (parsed) onEvent(parsed);
					}
				}
			} catch {
				// Se reintenta abajo
			}
			onStatus?.(false);
			if (closed) return;
			const wait = Math.min(15_000, 500 * 2 ** attempt++);
			await new Promise((r) => setTimeout(r, wait));
		}
	}

	void run();
	return {
		close() {
			closed = true;
			controller?.abort();
		}
	};
}
