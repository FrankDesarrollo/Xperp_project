<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		children?: Snippet;
	}

	let { title, children }: Props = $props();
</script>

<div class="empty">
	<p class="title">{title}</p>
	{#if children}<div class="text-muted small">{@render children()}</div>{/if}
</div>

<style>
	.empty {
		text-align: center;
		padding: var(--space-7) var(--space-5);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface);
		box-shadow: var(--shadow-sm);
		color: var(--color-text-muted);
	}

	.title {
		font-weight: var(--weight-medium);
		color: var(--color-text);
		margin-bottom: var(--space-2);
	}
</style>
