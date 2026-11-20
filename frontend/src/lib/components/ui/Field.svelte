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
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		box-shadow: var(--shadow-sm);
		min-height: 2.5rem;
		width: 100%;
		transition:
			border-color var(--duration-fast) var(--ease-out),
			box-shadow var(--duration-fast) var(--ease-out);
	}

	.field :global(input:focus),
	.field :global(select:focus),
	.field :global(textarea:focus) {
		outline: none;
		border-color: var(--color-primary);
		box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 22%, transparent);
	}

	.field :global(textarea) {
		min-height: 5rem;
		resize: vertical;
	}

	@media (pointer: coarse) {
		.field :global(input),
		.field :global(select) {
			min-height: 2.75rem;
		}
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
