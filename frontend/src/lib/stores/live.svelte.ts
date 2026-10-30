import {
	connectStream,
	type StreamEvent,
	type StreamEventName,
	type StreamHandle
} from '$lib/api/stream';
import type { Notification, SimulationClock } from '$lib/api/types';
import { clock } from './clock.svelte';
import { notifications } from './notifications.svelte';
import { toasts } from './toasts.svelte';

type Listener = (data: unknown) => void;

/**
 * Eventos en tiempo real. Abre una sola conexión SSE por sesión y la reparte entre las pantallas,
 * que se suscriben con {@link live.on} dentro de un `$effect`.
 */
class LiveStore {
	connected = $state(false);
	/** Último evento recibido de cada tipo; sirve para refrescar listas con `$effect`. */
	version = $state<Record<string, number>>({});

	private handle: StreamHandle | null = null;
	private listeners = new Map<StreamEventName, Set<Listener>>();

	start(token: string) {
		this.stop();
		this.handle = connectStream(
			token,
			(e) => this.dispatch(e),
			(ok) => (this.connected = ok)
		);
	}

	stop() {
		this.handle?.close();
		this.handle = null;
		this.connected = false;
	}

	/** Suscribe una función a un evento y devuelve la función para cancelar la suscripción. */
	on(event: StreamEventName, listener: Listener): () => void {
		let set = this.listeners.get(event);
		if (!set) {
			// eslint-disable-next-line svelte/prefer-svelte-reactivity -- registro de suscriptores, no es estado de la vista
			set = new Set();
			this.listeners.set(event, set);
		}
		set.add(listener);
		return () => set.delete(listener);
	}

	private dispatch(e: StreamEvent) {
		this.version = { ...this.version, [e.event]: (this.version[e.event] ?? 0) + 1 };
		if (e.event === 'clock.tick') {
			clock.apply(e.data as SimulationClock);
		} else if (e.event === 'notification.created') {
			const n = e.data as Notification;
			notifications.push(n);
			toasts.show(n.title, n.body, 'info');
		} else if (e.event === 'alert.created') {
			const a = e.data as { severity: string; message: string };
			if (a.severity === 'critical') toasts.show('Alerta crítica', a.message, 'danger');
		}
		this.listeners.get(e.event)?.forEach((l) => l(e.data));
	}
}

export const live = new LiveStore();
