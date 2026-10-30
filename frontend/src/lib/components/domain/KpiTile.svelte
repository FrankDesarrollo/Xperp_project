<script lang="ts">
	import type { Tone } from '$lib/utils/labels';

	interface Props {
		label: string;
		value: string;
		hint?: string;
		tone?: Tone;
	}

	let { label, value, hint, tone = 'neutral' }: Props = $props();
</script>

<div class="kpi {tone}">
	<span class="label">{label}</span>
	<span class="value">{value}</span>
	{#if hint}<span class="hint">{hint}</span>{/if}
</div>

<style>
	.kpi {
		display: grid;
		gap: var(--space-1);
		padding: var(--space-4);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		border-top: 3px solid var(--color-border);
	}

	.label {
		font-size: var(--text-sm);
		color: var(--color-text-muted);
	}

	.value {
		font-size: var(--text-2xl);
		font-weight: var(--weight-bold);
		font-variant-numeric: tabular-nums;
		line-height: 1.1;
	}

	.hint {
		font-size: var(--text-xs);
		color: var(--color-text-muted);
	}

	.success {
		border-top-color: var(--color-success);
	}

	.warning {
		border-top-color: var(--color-warning);
	}

	.danger {
		border-top-color: var(--color-danger);
	}

	.info {
		border-top-color: var(--color-info);
	}
</style>
