import type { SimulationClock } from '$lib/api/types';

export type SimulationStatus = 'paused' | 'running';
export type AiMode = 'active' | 'shadow' | 'stopped';

/** Reloj simulado y estado de la IA que muestra la barra superior (AGENTS.md, sección 11.2). */
class ClockStore {
	simTime = $state<string | null>(null);
	status = $state<SimulationStatus>('paused');
	aiMode = $state<AiMode>('active');
	dayNumber = $state(1);
	secondsPerDay = $state(20);
	scenarioCursor = $state(0);
	scenariosTotal = $state(0);

	/** Fecha simulada (aaaa-mm-dd) en Bogotá. */
	today = $derived(this.simTime ? this.simTime.slice(0, 10) : null);

	apply(c: Partial<SimulationClock>) {
		if (c.sim_time) this.simTime = c.sim_time;
		if (c.status) this.status = c.status as SimulationStatus;
		if (c.ai_mode) this.aiMode = c.ai_mode as AiMode;
		if (c.day_number !== undefined) this.dayNumber = c.day_number;
		if (c.seconds_per_day !== undefined) this.secondsPerDay = c.seconds_per_day;
		if (c.scenario_cursor !== undefined) this.scenarioCursor = c.scenario_cursor;
		if (c.scenarios_total !== undefined) this.scenariosTotal = c.scenarios_total;
	}
}

export const clock = new ClockStore();
