<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		open: boolean;
		title: string;
		onclose: () => void;
		children: Snippet;
		actions?: Snippet;
	}

	let { open, title, onclose, children, actions }: Props = $props();

	function onkeydown(e: KeyboardEvent) {
		if (open && e.key === 'Escape') onclose();
	}
</script>

<svelte:window {onkeydown} />

{#if open}
	<div class="overlay" role="presentation" onclick={onclose}></div>
	<div class="modal" role="dialog" aria-modal="true" aria-label={title}>
		<h2>{title}</h2>
		<div class="body">{@render children()}</div>
		{#if actions}<footer>{@render actions()}</footer>{/if}
	</div>
{/if}

<style>
	.overlay {
		position: fixed;
		inset: 0;
		background: var(--color-overlay);
		z-index: var(--z-modal);
	}

	.modal {
		position: fixed;
		top: 50%;
		left: 50%;
		transform: translate(-50%, -50%);
		width: min(560px, calc(100vw - 2rem));
		max-height: calc(100dvh - 2rem);
		overflow: auto;
		background: var(--color-surface);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-lg);
		padding: var(--space-5);
		z-index: calc(var(--z-modal) + 1);
	}

	h2 {
		font-size: var(--text-xl);
	}

	.body {
		display: grid;
		gap: var(--space-4);
	}

	footer {
		display: flex;
		justify-content: flex-end;
		gap: var(--space-3);
		margin-top: var(--space-5);
	}
</style>
