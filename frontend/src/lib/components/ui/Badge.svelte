<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { Tone } from '$lib/utils/labels';

	interface Props {
		tone?: Tone | 'ai';
		children: Snippet;
	}

	let { tone = 'neutral', children }: Props = $props();
</script>

<span class="badge {tone}">{@render children()}</span>

<style>
	.badge {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		padding: 0.125rem var(--space-2);
		border-radius: var(--radius-full);
		font-size: var(--text-xs);
		font-weight: var(--weight-medium);
		white-space: nowrap;
		background: var(--color-surface-muted);
		color: var(--color-text-muted);
		border: 1px solid transparent;
	}

	.info {
		background: var(--color-info-bg);
		color: var(--color-info);
	}

	.success {
		background: var(--color-success-bg);
		color: var(--color-success);
	}

	.warning {
		background: var(--color-warning-bg);
		color: var(--color-warning);
	}

	.danger {
		background: var(--color-danger-bg);
		color: var(--color-danger);
	}

	.ai {
		background: var(--color-ai-bg);
		color: var(--color-ai);
	}
</style>
