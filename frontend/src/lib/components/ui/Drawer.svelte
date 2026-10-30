<script lang="ts">
	import type { Snippet } from 'svelte';
	import { X } from '@lucide/svelte';

	interface Props {
		open: boolean;
		title: string;
		subtitle?: string;
		wide?: boolean;
		onclose: () => void;
		children: Snippet;
		actions?: Snippet;
	}

	let { open, title, subtitle, wide = false, onclose, children, actions }: Props = $props();

	function onkeydown(e: KeyboardEvent) {
		if (open && e.key === 'Escape') onclose();
	}
</script>

<svelte:window {onkeydown} />

{#if open}
	<div class="overlay" role="presentation" onclick={onclose}></div>
	<div class="drawer" class:wide role="dialog" aria-modal="true" aria-label={title} tabindex="-1">
		<header>
			<div>
				<h2>{title}</h2>
				{#if subtitle}<p class="text-muted small">{subtitle}</p>{/if}
			</div>
			<button class="close" onclick={onclose} aria-label="Cerrar"><X size={20} /></button>
		</header>
		<div class="body">{@render children()}</div>
		{#if actions}
			<footer>{@render actions()}</footer>
		{/if}
	</div>
{/if}

<style>
	.overlay {
		position: fixed;
		inset: 0;
		background: var(--color-overlay);
		z-index: var(--z-drawer);
	}

	.drawer {
		position: fixed;
		top: 0;
		right: 0;
		bottom: 0;
		width: min(560px, 100vw);
		background: var(--color-bg);
		z-index: calc(var(--z-drawer) + 1);
		display: flex;
		flex-direction: column;
		box-shadow: var(--shadow-lg);
		animation: slide var(--duration-base) ease-out;
	}

	.drawer.wide {
		width: min(820px, 100vw);
	}

	header {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: var(--space-4);
		padding: var(--space-5);
		border-bottom: 1px solid var(--color-border);
		background: var(--color-surface);
	}

	header h2 {
		margin: 0;
		font-size: var(--text-xl);
	}

	header p {
		margin: var(--space-1) 0 0;
	}

	.close {
		border: none;
		background: transparent;
		cursor: pointer;
		color: var(--color-text-muted);
		padding: var(--space-1);
		border-radius: var(--radius-md);
	}

	.close:hover {
		background: var(--color-surface-muted);
	}

	.body {
		flex: 1;
		overflow-y: auto;
		padding: var(--space-5);
		display: grid;
		gap: var(--space-5);
		align-content: start;
	}

	footer {
		display: flex;
		gap: var(--space-3);
		justify-content: flex-end;
		padding: var(--space-4) var(--space-5);
		border-top: 1px solid var(--color-border);
		background: var(--color-surface);
		flex-wrap: wrap;
	}

	@keyframes slide {
		from {
			transform: translateX(24px);
			opacity: 0;
		}
	}
</style>
