<script lang="ts">
	import type { Component, Snippet } from 'svelte';
	import AnimatedNumber from '$lib/components/ui/AnimatedNumber.svelte';
	import Sparkline from '$lib/components/charts/Sparkline.svelte';
	import type { Tone } from '$lib/utils/labels';

	/**
	 * Métrica principal del tablero: rótulo, valor animado, contexto y, opcionalmente, una tendencia
	 * (sparkline) o un avance contra una meta (barra). El estado va en texto, no solo en color.
	 */
	interface Props {
		label: string;
		icon: Component<{ size?: number; strokeWidth?: number }>;
		value: number;
		format: (n: number) => string;
		/** Línea de contexto bajo el valor. */
		hint?: string;
		/** Estado en palabras ("En meta", "Atención"...), con su tono. */
		status?: { label: string; tone: Tone };
		trend?: number[];
		trendMin?: number;
		/** Avance de 0 a 100 contra una meta o un tope. */
		progress?: number;
		/** Tono de la barra si no debe seguir al estado (p. ej. una proporción sana). */
		progressTone?: Tone;
		/** Marca de referencia en la barra (p. ej. 85 % del presupuesto). */
		threshold?: number;
		href?: string;
		children?: Snippet;
	}

	let {
		label,
		icon: Icon,
		value,
		format,
		hint,
		status,
		trend,
		trendMin,
		progress,
		progressTone,
		threshold,
		href,
		children
	}: Props = $props();

	let tone = $derived(status?.tone ?? 'neutral');
	let barTone = $derived(progressTone ?? tone);
	let color = $derived(
		barTone === 'danger'
			? 'var(--color-danger)'
			: barTone === 'warning'
				? 'var(--color-warning)'
				: barTone === 'success'
					? 'var(--color-success)'
					: 'var(--color-primary)'
	);
	let pct = $derived(progress == null ? null : Math.max(0, Math.min(100, progress)));
</script>

<svelte:element
	this={href ? 'a' : 'article'}
	{href}
	class="metric"
	class:link={!!href}
	aria-label={href ? `${label}: ${format(value)}` : undefined}
>
	<header>
		<span class="icon {tone}" aria-hidden="true"><Icon size={16} strokeWidth={2} /></span>
		<span class="eyebrow">{label}</span>
	</header>

	<div class="value-row">
		<span class="value"><AnimatedNumber {value} {format} /></span>
		{#if status}
			<span class="status {status.tone}"><span class="dot"></span>{status.label}</span>
		{/if}
	</div>
	{#if hint}<p class="hint">{hint}</p>{/if}

	{#if trend && trend.length > 1}
		<div class="trend"><Sparkline values={trend} {color} min={trendMin} /></div>
	{:else if pct != null}
		<div class="bar" role="presentation">
			<span class="fill" style:--w="{pct}%" style:--c={color}></span>
			{#if threshold != null}<span class="mark" style:left="{threshold}%"></span>{/if}
		</div>
	{/if}
	{@render children?.()}
</svelte:element>

<style>
	.metric {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-5);
		background: var(--color-surface);
		border: 1px solid var(--color-border-subtle);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		color: var(--color-text);
		text-decoration: none;
		overflow: hidden;
		transition:
			box-shadow var(--duration-base) var(--ease-out),
			transform var(--duration-base) var(--ease-out),
			border-color var(--duration-base) var(--ease-out);
	}

	.link:hover {
		transform: translateY(-2px);
		box-shadow: var(--shadow-md);
		border-color: var(--color-border);
	}

	header {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.icon {
		display: inline-grid;
		place-items: center;
		width: 1.75rem;
		height: 1.75rem;
		border-radius: var(--radius-md);
		background: var(--color-surface-muted);
		color: var(--color-text-muted);
	}

	.icon.success {
		background: var(--color-success-bg);
		color: var(--color-success);
	}
	.icon.warning {
		background: var(--color-warning-bg);
		color: var(--color-warning);
	}
	.icon.danger {
		background: var(--color-danger-bg);
		color: var(--color-danger);
	}
	.icon.info {
		background: var(--color-info-bg);
		color: var(--color-info);
	}

	.value-row {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--space-2);
		flex-wrap: wrap;
		margin-top: var(--space-2);
	}

	.status {
		display: inline-flex;
		align-items: center;
		gap: 0.375rem;
		font-size: var(--text-xs);
		font-weight: var(--weight-medium);
		color: var(--color-text-muted);
		white-space: nowrap;
	}

	.dot {
		width: 0.4rem;
		height: 0.4rem;
		border-radius: var(--radius-full);
		background: currentColor;
	}

	.status.success {
		color: var(--color-success);
	}
	.status.warning {
		color: var(--color-warning);
	}
	.status.danger {
		color: var(--color-danger);
	}
	.status.info {
		color: var(--color-info);
	}

	.value {
		font-size: var(--text-3xl);
		font-weight: var(--weight-bold);
		letter-spacing: var(--tracking-tight);
		line-height: 1.05;
	}

	.hint {
		margin: 0;
		font-size: var(--text-sm);
		color: var(--color-text-muted);
	}

	.trend {
		margin: var(--space-2) calc(var(--space-5) * -1) calc(var(--space-5) * -1);
	}

	.bar {
		position: relative;
		margin-top: auto;
		height: 6px;
		border-radius: var(--radius-full);
		background: var(--color-track);
	}

	.fill {
		position: absolute;
		inset: 0 auto 0 0;
		width: var(--w);
		border-radius: inherit;
		background: var(--c);
		transform-origin: left;
		animation: grow 1.2s var(--ease-out) 0.3s both;
	}

	.mark {
		position: absolute;
		top: -3px;
		bottom: -3px;
		width: 2px;
		border-radius: 1px;
		background: var(--color-text-muted);
		opacity: 0.5;
	}

	@keyframes grow {
		from {
			transform: scaleX(0);
		}
	}
</style>
