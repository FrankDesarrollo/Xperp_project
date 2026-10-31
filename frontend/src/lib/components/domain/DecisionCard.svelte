<script lang="ts">
	import { resolve } from '$app/paths';
	import { Bot, Sparkles, Undo2 } from '@lucide/svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import StatusBadge from './StatusBadge.svelte';
	import FactsList from './FactsList.svelte';
	import DataTable from './DataTable.svelte';
	import type { AiDecision } from '$lib/api/types';
	import { formatCop, formatDateTime, formatPct } from '$lib/utils/format';
	import { ACTION_TYPE, DECISION_STATUS, DECISION_TYPE, LEVEL_LABEL } from '$lib/utils/labels';

	/**
	 * Tarjeta única de una decisión de la IA, igual en todas las pantallas (AGENTS.md, 11.2).
	 * En modo compacto muestra el resumen; en modo completo, los datos de entrada, las alternativas
	 * y la acción propuesta.
	 */
	interface Props {
		decision: AiDecision;
		compact?: boolean;
		canRevert?: boolean;
		onopen?: (d: AiDecision) => void;
		onrevert?: (d: AiDecision) => void;
	}

	let { decision: d, compact = false, canRevert = false, onopen, onrevert }: Props = $props();

	const REF_ROUTE: Record<string, string> = {
		purchase_order: '/compras',
		sales_order: '/ventas',
		stock_transfer: '/inventario',
		warehouse_task: '/bodega',
		shipment: '/despachos',
		product: '/inventario',
		inventory: '/inventario',
		ai_decision: '/ia/decisiones'
	};

	function refHref(type: string, id: number): string {
		const base = REF_ROUTE[type] ?? '/tablero';
		const key =
			type === 'purchase_order'
				? 'oc'
				: type === 'product' || type === 'inventory'
					? 'product'
					: 'id';
		return `${resolve(base as '/tablero')}?${key}=${id}`;
	}
</script>

<article class="decision" class:compact>
	<header>
		<div class="tags">
			<Badge tone="ai"><Bot size={12} />{DECISION_TYPE[d.decision_type] ?? d.decision_type}</Badge>
			<StatusBadge map={DECISION_STATUS} value={d.status} />
			{#if d.llm_used}
				<Badge tone="ai"><Sparkles size={12} />LLM {d.llm_model ?? ''}</Badge>
			{/if}
		</div>
		<span class="text-muted small">{formatDateTime(d.sim_time)}</span>
	</header>

	{#if onopen}
		<button class="title as-link" onclick={() => onopen?.(d)}
			>{d.title ?? ACTION_TYPE[d.action_type ?? ''] ?? 'Decisión'}</button
		>
	{:else}
		<h3 class="title">{d.title ?? ACTION_TYPE[d.action_type ?? ''] ?? 'Decisión'}</h3>
	{/if}

	<p class="reasoning">{d.reasoning}</p>

	<div class="meta small">
		{#if d.amount}<span><strong>{formatCop(d.amount)}</strong></span>{/if}
		{#if d.autonomy_level !== null && d.autonomy_level !== undefined}
			<span class="text-muted">{LEVEL_LABEL[d.autonomy_level]}</span>
		{/if}
		<span class="text-muted">Confianza {formatPct(d.confidence, true, 0)}</span>
		{#if d.result_message}<span>{d.result_message}</span>{/if}
	</div>

	{#if d.references.length > 0}
		<div class="refs small">
			{#each d.references.slice(0, compact ? 4 : 20) as r (r.type + r.id)}
				<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- refHref ya usa resolve() -->
				<a href={refHref(r.type, r.id)}>{r.label}</a>
			{/each}
		</div>
	{/if}

	{#if !compact}
		<section>
			<h4>Política</h4>
			<p class="small">
				<StatusBadge map={DECISION_STATUS} value={d.policy_result} />
				{d.policy_reason ?? ''}
				{#if d.approved_by_name}· aprobó {d.approved_by_name}{/if}
			</p>
		</section>
		<section>
			<h4>Qué vio</h4>
			<FactsList data={d.input_snapshot} />
		</section>
		{#if d.alternatives.length > 0}
			<section>
				<h4>Alternativas</h4>
				<DataTable rows={d.alternatives} />
			</section>
		{/if}
		<section>
			<h4>Acción propuesta · {ACTION_TYPE[d.action_type ?? ''] ?? d.action_type}</h4>
			<FactsList data={d.proposed_action} />
		</section>
		{#if d.llm_used}
			<section class="small text-muted">
				Modelo {d.llm_model ?? '—'} · prompt {d.prompt_version ?? '—'} · tokens {d.tokens_in ??
					'—'}/{d.tokens_out ?? '—'} · {d.latency_ms ?? '—'} ms
			</section>
		{/if}
		{#if d.reversible && canRevert}
			<div>
				<Button variant="danger" onclick={() => onrevert?.(d)}
					><Undo2 size={16} />Revertir decisión</Button
				>
			</div>
		{/if}
	{/if}
</article>

<style>
	.decision {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-left: 3px solid var(--color-ai);
		border-radius: var(--radius-lg);
	}

	header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--space-3);
		flex-wrap: wrap;
	}

	.tags {
		display: flex;
		gap: var(--space-2);
		flex-wrap: wrap;
	}

	.title {
		margin: 0;
		font-size: var(--text-md);
		font-weight: var(--weight-bold);
		text-align: left;
	}

	.as-link {
		background: none;
		border: none;
		padding: 0;
		cursor: pointer;
		color: var(--color-text);
	}

	.as-link:hover {
		color: var(--color-primary);
		text-decoration: underline;
	}

	.reasoning {
		margin: 0;
		font-size: var(--text-sm);
		line-height: 1.55;
	}

	.compact .reasoning {
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.meta,
	.refs {
		display: flex;
		gap: var(--space-3);
		flex-wrap: wrap;
	}

	@media (pointer: coarse) {
		.refs a {
			display: inline-flex;
			align-items: center;
			min-height: 2.75rem;
		}
	}

	section {
		display: grid;
		gap: var(--space-2);
	}

	h4 {
		margin: 0;
		font-size: var(--text-sm);
		color: var(--color-text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	section p {
		margin: 0;
	}
</style>
