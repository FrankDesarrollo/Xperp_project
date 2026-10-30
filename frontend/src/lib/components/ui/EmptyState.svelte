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
		padding: var(--space-7) var(--space-4);
		border: 1px dashed var(--color-border);
		border-radius: var(--radius-lg);
		color: var(--color-text-muted);
	}

	.title {
		font-weight: var(--weight-medium);
		color: var(--color-text);
		margin-bottom: var(--space-2);
	}
</style>
