<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		label: string;
		hint?: string;
		error?: string;
		children: Snippet;
	}

	let { label, hint, error, children }: Props = $props();
</script>

<label class="field">
	<span class="label">{label}</span>
	{@render children()}
	{#if error}
		<span class="error" role="alert">{error}</span>
	{:else if hint}
		<span class="hint">{hint}</span>
	{/if}
</label>

<style>
	.field {
		display: grid;
		gap: var(--space-1);
		font-size: var(--text-sm);
		min-width: 0;
	}

	.label {
		color: var(--color-text-muted);
		font-weight: var(--weight-medium);
	}

	.field :global(input),
	.field :global(select),
	.field :global(textarea) {
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		min-height: 2.375rem;
		width: 100%;
	}

	.field :global(textarea) {
		min-height: 5rem;
		resize: vertical;
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--text-xs);
	}

	.error {
		color: var(--color-danger);
		font-size: var(--text-xs);
	}
</style>
