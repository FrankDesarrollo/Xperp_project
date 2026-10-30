<script lang="ts">
	import type { Snippet } from 'svelte';
	import { CircleAlert, RotateCw } from '@lucide/svelte';
	import type { Loader } from '$lib/utils/loader.svelte';
	import Button from './Button.svelte';

	/**
	 * Muestra los estados de una vista con datos: cargando, error con salida y contenido.
	 * La vista vacía la resuelve cada pantalla con `EmptyState`, porque solo ella sabe qué
	 * hacer cuando no hay datos.
	 */
	interface Props {
		loader: Loader;
		/** Qué se está cargando, con su artículo: «los pedidos» → «Cargando los pedidos…». */
		what: string;
		onretry: () => void;
		children: Snippet;
	}

	let { loader, what, onretry, children }: Props = $props();
</script>

{#if !loader.loaded && loader.loading}
	<p class="loading text-muted" role="status">
		<span class="spinner" aria-hidden="true"></span>Cargando {what}…
	</p>
{:else if !loader.loaded}
	<div class="error" role="alert">
		<CircleAlert size={20} aria-hidden="true" />
		<div>
			<strong>No se pudo cargar esta información.</strong>
			<p>{loader.error}</p>
		</div>
		<Button onclick={onretry}><RotateCw size={16} aria-hidden="true" />Reintentar</Button>
	</div>
{:else}
	{#if loader.error}
		<div class="error compact" role="alert">
			<CircleAlert size={18} aria-hidden="true" />
			<p>No se pudo actualizar: {loader.error} Se muestran los últimos datos.</p>
			<Button size="sm" onclick={onretry}>Reintentar</Button>
		</div>
	{/if}
	{@render children()}
{/if}

<style>
	.loading {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-5) 0;
		margin: 0;
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

	.error {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
		flex-wrap: wrap;
		padding: var(--space-4);
		border: 1px solid var(--color-danger);
		border-radius: var(--radius-lg);
		background: var(--color-danger-bg);
		color: var(--color-text);
	}

	.error > :global(svg) {
		color: var(--color-danger);
		flex: none;
		margin-top: 2px;
	}

	.error div {
		flex: 1;
		min-width: 12rem;
	}

	.error p {
		margin: var(--space-1) 0 0;
	}

	.error.compact {
		align-items: center;
		padding: var(--space-2) var(--space-3);
		margin-bottom: var(--space-4);
	}

	.error.compact p {
		flex: 1;
		margin: 0;
		font-size: var(--text-sm);
		min-width: 12rem;
	}
</style>
