import { api, unwrap, ApiError } from '$lib/api/client';
import type { AssistantMessage } from '$lib/api/types';
import { clock } from '$lib/stores/clock.svelte';

/** Preguntas de ejemplo: muestran lo que el asistente sabe responder. */
export const ASSISTANT_SUGGESTIONS = [
	'¿Qué me recomiendas reponer?',
	'¿Qué se agota esta semana?',
	'¿Hay exceso de inventario?',
	'¿Por qué se le compró a Mayorista Central?',
	'¿Cómo van las ventas del mes?',
	'¿Hay aprobaciones pendientes?'
];

/**
 * Conversación con el asistente. Es una sola para toda la sesión: la pantalla «Asistente» y el
 * panel flotante muestran la misma, así una pregunta hecha desde Inventario sigue en el chat.
 */
class AssistantStore {
	conversationId = $state<string | null>(null);
	messages = $state<AssistantMessage[]>([]);
	sending = $state(false);
	/** Panel flotante abierto. */
	open = $state(false);

	async send(text: string) {
		const content = text.trim();
		if (!content || this.sending) return;
		this.sending = true;
		this.messages = [
			...this.messages,
			{
				id: -Date.now(),
				role: 'user',
				content,
				created_at: clock.simTime ?? new Date().toISOString()
			}
		];
		try {
			const reply = await unwrap(
				api.POST('/assistant/messages', {
					body: { conversation_id: this.conversationId, content }
				})
			);
			this.conversationId = reply.conversation_id;
			this.messages = [...this.messages, reply.message];
		} catch (e) {
			this.messages = [
				...this.messages,
				{
					id: -Date.now(),
					role: 'assistant',
					content:
						e instanceof ApiError
							? e.message
							: 'No pude responder en este momento. Revisa la conexión y vuelve a preguntar.',
					created_at: clock.simTime ?? new Date().toISOString()
				}
			];
		} finally {
			this.sending = false;
		}
	}

	/** Abre el panel y hace la pregunta: lo usan los botones «Preguntar a la IA». */
	ask(text: string) {
		this.open = true;
		void this.send(text);
	}

	reset() {
		this.messages = [];
		this.conversationId = null;
	}
}

export const assistant = new AssistantStore();
