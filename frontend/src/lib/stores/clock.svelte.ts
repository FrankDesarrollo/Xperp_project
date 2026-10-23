export type SimulationStatus = 'paused' | 'running';
export type AiMode = 'active' | 'shadow' | 'stopped';

/** Reloj simulado y estado de la IA que muestra la barra superior (AGENTS.md, sección 11.2). */
class ClockStore {
	simTime = $state<string | null>(null);
	status = $state<SimulationStatus>('paused');
	aiMode = $state<AiMode>('active');
}

export const clock = new ClockStore();
