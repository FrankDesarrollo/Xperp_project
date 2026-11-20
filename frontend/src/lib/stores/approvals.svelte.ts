import { api } from '$lib/api/client';
import { live } from './live.svelte';

/**
 * Cuántas aprobaciones esperan al rol de la sesión. Se pide una vez y luego solo cuando llega un
 * evento que la puede cambiar (aprobación pedida o decisión de la IA), agrupando ráfagas de eventos
 * en una sola petición.
 */
class ApprovalsStore {
	pending = $state(0);
	#timer: ReturnType<typeof setTimeout> | null = null;
	#offs: (() => void)[] = [];

	async refresh() {
		const { data } = await api.GET('/approvals', {
			params: { query: { status: 'pending', mine: true, size: 1 } }
		});
		if (data) this.pending = data.total;
	}

	start() {
		this.stop();
		void this.refresh();
		const later = () => {
			if (this.#timer) return;
			this.#timer = setTimeout(() => {
				this.#timer = null;
				void this.refresh();
			}, 1500);
		};
		this.#offs = [live.on('approval.requested', later), live.on('ai.decision', later)];
	}

	stop() {
		this.#offs.forEach((off) => off());
		this.#offs = [];
		if (this.#timer) clearTimeout(this.#timer);
		this.#timer = null;
	}
}

export const approvals = new ApprovalsStore();
