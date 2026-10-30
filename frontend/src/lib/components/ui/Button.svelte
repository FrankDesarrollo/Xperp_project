<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	interface Props extends HTMLButtonAttributes {
		variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'ai';
		size?: 'sm' | 'md' | 'lg';
		loading?: boolean;
		children: Snippet;
	}

	let {
		variant = 'secondary',
		size = 'md',
		loading = false,
		disabled,
		type = 'button',
		children,
		...rest
	}: Props = $props();
</script>

<button
	{type}
	class="btn {variant} {size}"
	disabled={disabled || loading}
	aria-busy={loading || undefined}
	{...rest}
>
	{#if loading}<span class="spinner" aria-hidden="true"></span>{/if}
	{@render children()}
</button>

<style>
	.btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		border-radius: var(--radius-md);
		border: 1px solid var(--color-border);
		background: var(--color-surface);
		color: var(--color-text);
		font-weight: var(--weight-medium);
		cursor: pointer;
		transition:
			background var(--duration-fast),
			border-color var(--duration-fast);
		white-space: nowrap;
	}

	.btn:hover:not(:disabled) {
		background: var(--color-surface-muted);
	}

	.btn:disabled {
		opacity: 0.55;
		cursor: not-allowed;
	}

	.sm {
		padding: var(--space-1) var(--space-3);
		font-size: var(--text-sm);
	}

	.md {
		padding: var(--space-2) var(--space-4);
		font-size: var(--text-sm);
	}

	.lg {
		padding: var(--space-3) var(--space-5);
		font-size: var(--text-lg);
		min-height: 3.25rem;
	}

	.primary {
		background: var(--color-primary);
		border-color: var(--color-primary);
		color: var(--color-on-primary);
	}

	.primary:hover:not(:disabled) {
		background: var(--color-primary-hover);
	}

	.ghost {
		background: transparent;
		border-color: transparent;
	}

	.danger {
		color: var(--color-danger);
		border-color: var(--color-danger);
	}

	.danger:hover:not(:disabled) {
		background: var(--color-danger-bg);
	}

	.ai {
		background: var(--color-ai);
		border-color: var(--color-ai);
		color: var(--color-surface);
	}

	.ai:hover:not(:disabled) {
		filter: brightness(1.08);
		background: var(--color-ai);
	}

	.spinner {
		width: 1em;
		height: 1em;
		border: 2px solid currentColor;
		border-right-color: transparent;
		border-radius: var(--radius-full);
		animation: spin 0.7s linear infinite;
	}

	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}
</style>
