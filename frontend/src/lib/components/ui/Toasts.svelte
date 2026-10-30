<script lang="ts">
	import { toasts } from '$lib/stores/toasts.svelte';
</script>

<div class="toasts" aria-live="polite">
	{#each toasts.items as t (t.id)}
		<div class="toast {t.kind}" role="status">
			<strong>{t.title}</strong>
			{#if t.body}<p>{t.body}</p>{/if}
			<button onclick={() => toasts.dismiss(t.id)} aria-label="Cerrar aviso">×</button>
		</div>
	{/each}
</div>

<style>
	.toasts {
		position: fixed;
		bottom: var(--space-5);
		right: var(--space-5);
		display: grid;
		gap: var(--space-3);
		z-index: var(--z-toast);
		width: min(380px, calc(100vw - 2rem));
	}

	.toast {
		position: relative;
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-left: 4px solid var(--color-info);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-md);
		padding: var(--space-3) var(--space-6) var(--space-3) var(--space-4);
		font-size: var(--text-sm);
		animation: pop var(--duration-base) ease-out;
	}

	.toast p {
		margin: var(--space-1) 0 0;
		color: var(--color-text-muted);
	}

	.success {
		border-left-color: var(--color-success);
	}

	.warning {
		border-left-color: var(--color-warning);
	}

	.danger {
		border-left-color: var(--color-danger);
	}

	button {
		position: absolute;
		top: var(--space-2);
		right: var(--space-2);
		border: none;
		background: transparent;
		cursor: pointer;
		font-size: var(--text-lg);
		color: var(--color-text-muted);
	}

	@keyframes pop {
		from {
			transform: translateY(8px);
			opacity: 0;
		}
	}
</style>
