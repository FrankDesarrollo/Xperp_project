<script lang="ts">
	import { Coins, Info, TrendingUp, TriangleAlert } from '@lucide/svelte';
	import type { ImpactItem } from '$lib/api/types';
	import { formatImpact } from '$lib/utils/format';
	import { IMPACT_EFFECT } from '$lib/utils/labels';

	/**
	 * Impacto económico u operativo de una propuesta (ADR 0004): qué gasta, qué protege y qué
	 * arriesga. Lo calcula el backend con sus datos, no el LLM. Cada cifra lleva ícono y texto, no
	 * solo color.
	 */
	interface Props {
		items: ImpactItem[];
		max?: number;
	}

	let { items, max }: Props = $props();
	let shown = $derived(max ? items.slice(0, max) : items);

	const ICON = { cost: Coins, benefit: TrendingUp, risk: TriangleAlert, info: Info } as const;
	const INFO = { label: 'Dato', tone: 'neutral' } as const;
</script>

{#if shown.length > 0}
	<ul class="impact" aria-label="Impacto de la propuesta">
		{#each shown as item (item.label)}
			{@const Icon = ICON[item.effect as keyof typeof ICON] ?? Info}
			{@const effect = IMPACT_EFFECT[item.effect] ?? INFO}
			<li class={effect.tone}>
				<Icon size={14} aria-hidden="true" />
				<span class="visually-hidden">{effect.label}:</span>
				<span class="label">{item.label}</span>
				<strong class="value numeric">{formatImpact(item.value, item.unit)}</strong>
				{#if item.note}<span class="note">{item.note}</span>{/if}
			</li>
		{/each}
	</ul>
{/if}

<style>
	.impact {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-2);
		grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
	}

	li {
		display: grid;
		grid-template-columns: auto 1fr;
		column-gap: var(--space-2);
		align-items: baseline;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-md);
		background: var(--color-surface-muted);
		border-left: 3px solid var(--color-border);
		font-size: var(--text-sm);
	}

	li :global(svg) {
		grid-row: span 2;
		align-self: center;
	}

	.label {
		color: var(--color-text-muted);
	}

	.value {
		grid-column: 2;
		font-size: var(--text-md);
	}

	.note {
		grid-column: 2;
		color: var(--color-text-muted);
		font-size: var(--text-xs);
	}

	.warning {
		border-left-color: var(--color-warning);
	}

	.warning :global(svg) {
		color: var(--color-warning);
	}

	.success {
		border-left-color: var(--color-success);
	}

	.success :global(svg) {
		color: var(--color-success);
	}

	.danger {
		border-left-color: var(--color-danger);
	}

	.danger :global(svg) {
		color: var(--color-danger);
	}
</style>
