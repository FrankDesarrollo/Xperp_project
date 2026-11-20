import type { StreamEventName } from '$lib/api/stream';
import { live } from '$lib/stores/live.svelte';

interface Options {
	/** Espera tras el primer evento para agrupar ráfagas en una sola recarga (ms). */
	delay?: number;
	/** Recarga solo si se cumple (p. ej. estar en la primera página). */
	when?: () => boolean;
}

/**
 * Recarga una vista cuando llegan eventos en vivo, sin saturar el backend:
 * agrupa ráfagas de eventos en una sola recarga y, con la pestaña oculta, no pide nada; al volver,
 * recarga una vez si algo cambió mientras tanto. Devuelve la función para dejar de escuchar.
 */
export function liveRefresh(
	events: StreamEventName[],
	run: () => void,
	{ delay = 3000, when }: Options = {}
): () => void {
	let timer: ReturnType<typeof setTimeout> | null = null;
	let dirty = false;

	const fire = () => {
		timer = null;
		if (document.hidden) {
			dirty = true;
			return;
		}
		run();
	};

	const onEvent = () => {
		if (when && !when()) return;
		if (document.hidden) {
			dirty = true;
			return;
		}
		if (!timer) timer = setTimeout(fire, delay);
	};

	const onVisible = () => {
		if (!document.hidden && dirty) {
			dirty = false;
			run();
		}
	};

	const offs = events.map((e) => live.on(e, onEvent));
	document.addEventListener('visibilitychange', onVisible);

	return () => {
		offs.forEach((off) => off());
		document.removeEventListener('visibilitychange', onVisible);
		if (timer) clearTimeout(timer);
	};
}
