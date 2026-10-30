<script lang="ts">
	import { resolve } from '$app/paths';
	import {
		ArrowLeftRight,
		ClipboardCheck,
		MessageSquare,
		PackageX,
		ShoppingCart,
		Truck
	} from '@lucide/svelte';
	import type { StockRecommendation } from '$lib/api/types';
	import { assistant } from '$lib/stores/assistant.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import StockBadge from './StockBadge.svelte';
	import { formatCop, formatDate, formatNumber } from '$lib/utils/format';
	import { RECOMMENDATION_KIND, RECOMMENDATION_PRIORITY } from '$lib/utils/labels';

	/** Una recomendación de la IA sobre una existencia, con su porqué y qué hacer con ella. */
	interface Props {
		rec: StockRecommendation;
		/** Abre el detalle de la existencia (kardex y pronóstico). */
		onopen?: (rec: StockRecommendation) => void;
		compact?: boolean;
	}

	let { rec, onopen, compact = false }: Props = $props();

	const ICONS = {
		buy: ShoppingCart,
		transfer: ArrowLeftRight,
		incoming: Truck,
		pending_approval: ClipboardCheck,
		excess: PackageX
	} as const;

	let Icon = $derived(ICONS[rec.kind as keyof typeof ICONS] ?? ShoppingCart);
	let kind = $derived(
		RECOMMENDATION_KIND[rec.kind] ?? { label: rec.kind, tone: 'neutral' as const }
	);
	let priority = $derived(
		RECOMMENDATION_PRIORITY[rec.priority] ?? { label: rec.priority, tone: 'neutral' as const }
	);
</script>

<article class="rec {rec.priority}" class:compact aria-label="{priority.label}: {rec.title}">
	<header>
		<span class="icon" aria-hidden="true"><Icon size={18} /></span>
		<div class="head">
			<div class="badges">
				<Badge tone={priority.tone}>{priority.label}</Badge>
				<Badge tone={kind.tone}>{kind.label}</Badge>
			</div>
			<strong class="title">{rec.title}</strong>
			<span class="small text-muted">
				<span class="mono">{rec.sku}</span> · {rec.product_name} · {rec.warehouse_code}
			</span>
		</div>
		{#if !compact}<StockBadge status={rec.status} />{/if}
	</header>

	{#if !compact}
		<p class="reason small">{rec.reason}</p>
		<dl class="facts small">
			<div>
				<dt>Disponible</dt>
				<dd>{formatNumber(rec.available)} und</dd>
			</div>
			<div>
				<dt>Cobertura</dt>
				<dd>
					{rec.days_of_cover == null ? 'sin demanda' : `${formatNumber(rec.days_of_cover, 1)} d`}
				</dd>
			</div>
			{#if rec.amount}
				<div>
					<dt>Valor estimado</dt>
					<dd>{formatCop(rec.amount)}</dd>
				</div>
			{/if}
			{#if rec.expected_at}
				<div>
					<dt>Llega</dt>
					<dd>{formatDate(rec.expected_at)}</dd>
				</div>
			{/if}
		</dl>
		<div class="actions">
			{#if rec.kind === 'pending_approval'}
				<a class="btn-link" href={resolve('/ia/aprobaciones')}>Ir a aprobaciones</a>
			{/if}
			{#if onopen}
				<Button size="sm" onclick={() => onopen(rec)}>Ver existencia</Button>
			{/if}
			<Button
				size="sm"
				variant="ghost"
				onclick={() => assistant.ask(`¿Qué recomiendas con ${rec.sku} en ${rec.warehouse_code}?`)}
			>
				<MessageSquare size={14} aria-hidden="true" />Preguntar a la IA
			</Button>
		</div>
	{/if}
</article>

<style>
	.rec {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-left: 4px solid var(--color-border);
		border-radius: var(--radius-md);
	}

	.rec.high {
		border-left-color: var(--color-danger);
	}

	.rec.medium {
		border-left-color: var(--color-warning);
	}

	.rec.compact {
		padding: var(--space-3);
	}

	header {
		display: flex;
		gap: var(--space-3);
		align-items: flex-start;
	}

	.icon {
		width: 2rem;
		height: 2rem;
		border-radius: var(--radius-full);
		display: grid;
		place-items: center;
		background: var(--color-ai-bg);
		color: var(--color-ai);
		flex: none;
	}

	.head {
		display: grid;
		gap: var(--space-1);
		flex: 1;
		min-width: 0;
	}

	.badges {
		display: flex;
		gap: var(--space-1);
		flex-wrap: wrap;
	}

	.title {
		overflow-wrap: anywhere;
	}

	.reason {
		margin: 0;
		color: var(--color-text-muted);
		max-width: 80ch;
	}

	.facts {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2) var(--space-5);
		margin: 0;
	}

	.facts div {
		display: grid;
	}

	.facts dt {
		color: var(--color-text-muted);
		font-size: var(--text-xs);
	}

	.facts dd {
		margin: 0;
		font-weight: var(--weight-medium);
		font-variant-numeric: tabular-nums;
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		align-items: center;
	}

	.btn-link {
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
		padding: var(--space-1) var(--space-2);
	}
</style>
