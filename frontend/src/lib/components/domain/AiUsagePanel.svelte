<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { api, unwrap } from '$lib/api/client';
	import type { AiUsage } from '$lib/api/types';
	import KpiTile from './KpiTile.svelte';
	import { formatNumber, formatPct } from '$lib/utils/format';

	/**
	 * Cuándo y cuánto se usó la IA en el mes (ADR 0004). Muestra que no hay una IA corriendo todo el
	 * tiempo: se activa por evento, condición, pedido o revisión programada, y casi todo lo resuelven
	 * las reglas sin llamar al LLM.
	 */
	let usage = $state<AiUsage | null>(null);
	const loader = new Loader();

	function load() {
		return loader.run(async () => {
			usage = await unwrap(api.GET('/ai/usage'));
		});
	}

	let rulesShare = $derived(
		usage && usage.decisions_total > 0 ? usage.decided_by_rules / usage.decisions_total : 1
	);

	onMount(() => {
		void load();
	});
</script>

<section class="card usage" aria-labelledby="usage-title">
	<h2 id="usage-title">Cómo se usó la IA este mes</h2>
	<p class="small text-muted">
		La IA no está corriendo todo el tiempo: se activa cuando pasa algo, cuando se cruza un umbral,
		cuando alguien lo pide o en una revisión programada. Las reglas resuelven lo que tiene respuesta
		exacta; el LLM solo entra en empates, excepciones, resúmenes y preguntas.
	</p>
	<LoadState {loader} what="el uso de la IA" onretry={load}>
		{#if usage}
			<div class="grid-kpi">
				<KpiTile
					label="Decisiones"
					value={formatNumber(usage.decisions_total)}
					hint="{formatPct(rulesShare, true, 0)} resueltas por reglas"
					tone="info"
				/>
				<KpiTile
					label="Qué las activó"
					value={formatNumber(usage.by_trigger.event)}
					hint="por evento · {usage.by_trigger.condition} por condición · {usage.by_trigger
						.schedule} programadas · {usage.by_trigger.request} a pedido"
				/>
				<KpiTile
					label="Llamadas al LLM"
					value={formatNumber(usage.llm_calls)}
					hint="Tope de {usage.llm_limit_per_day} por día · {usage.decided_with_llm} decisiones y {usage.assistant_questions} preguntas"
					tone={usage.llm_calls === 0 ? 'success' : 'info'}
				/>
				<KpiTile
					label="Tokens"
					value={formatNumber(usage.tokens_in + usage.tokens_out)}
					hint={usage.estimated_cost_usd !== null && usage.estimated_cost_usd !== undefined
						? `Costo estimado US$ ${formatNumber(Number(usage.estimated_cost_usd), 2)}`
						: 'Sin precio configurado (LLM_PRICE_*)'}
				/>
				<KpiTile
					label="Acciones financieras sin aprobación"
					value={formatNumber(usage.autonomous_financial_actions)}
					hint={usage.autonomous_financial_actions === 0
						? 'Tiene que ser 0: todo lo aprobó una persona'
						: 'La regla de aprobación se rompió: revisa la bitácora'}
					tone={usage.autonomous_financial_actions === 0 ? 'success' : 'danger'}
				/>
			</div>
		{/if}
	</LoadState>
</section>

<style>
	.usage {
		display: grid;
		gap: var(--space-3);
		margin-bottom: var(--space-4);
	}

	.usage h2 {
		margin: 0;
		font-size: var(--text-md);
	}

	.usage p {
		margin: 0;
	}
</style>
