<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { clock } from '$lib/stores/clock.svelte';

	let { children } = $props();

	// Menú lateral. El filtrado por rol (lib/utils/permissions.ts) y el guard de sesión
	// se conectan cuando exista el login (fase 1).
	const menu = [
		{ href: '/tablero', label: 'Tablero' },
		{ href: '/inventario', label: 'Inventario' },
		{ href: '/ventas', label: 'Ventas' },
		{ href: '/compras', label: 'Compras' },
		{ href: '/bodega', label: 'Bodega' },
		{ href: '/despachos', label: 'Despachos' },
		{ href: '/ia/decisiones', label: 'IA · Decisiones' },
		{ href: '/ia/aprobaciones', label: 'IA · Aprobaciones' },
		{ href: '/ia/politicas', label: 'IA · Políticas' },
		{ href: '/asistente', label: 'Asistente' },
		{ href: '/simulacion', label: 'Simulación' },
		{ href: '/configuracion', label: 'Configuración' }
	] as const;
</script>

<div class="shell">
	<nav class="sidebar" aria-label="Menú principal">
		<p class="brand">Xperp</p>
		<ul>
			{#each menu as item (item.href)}
				<li>
					<a
						href={resolve(item.href)}
						aria-current={page.url.pathname.startsWith(item.href) ? 'page' : undefined}
					>
						{item.label}
					</a>
				</li>
			{/each}
		</ul>
	</nav>

	<header class="topbar">
		<span class="text-muted">Hora simulada: {clock.simTime ?? '—'}</span>
		<span class="text-muted">IA: {clock.aiMode}</span>
	</header>

	<main class="content">
		{@render children()}
	</main>
</div>

<style>
	.shell {
		display: grid;
		grid-template-columns: var(--sidebar-width) 1fr;
		grid-template-rows: var(--topbar-height) 1fr;
		grid-template-areas:
			'sidebar topbar'
			'sidebar content';
		min-height: 100dvh;
	}

	.sidebar {
		grid-area: sidebar;
		padding: var(--space-4);
		background: var(--color-surface);
		border-right: 1px solid var(--color-border);
	}

	.brand {
		font-size: var(--text-lg);
		font-weight: var(--weight-bold);
		color: var(--color-primary);
	}

	.sidebar ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-1);
	}

	.sidebar a {
		display: block;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-md);
		color: var(--color-text);
		text-decoration: none;
	}

	.sidebar a:hover {
		background: var(--color-surface-muted);
	}

	.sidebar a[aria-current='page'] {
		background: var(--color-primary);
		color: var(--color-on-primary);
	}

	.topbar {
		grid-area: topbar;
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: var(--space-5);
		padding: 0 var(--space-5);
		background: var(--color-surface);
		border-bottom: 1px solid var(--color-border);
		font-size: var(--text-sm);
	}

	.content {
		grid-area: content;
		padding: var(--space-5);
	}

	@media (max-width: 768px) {
		.shell {
			grid-template-columns: 1fr;
			grid-template-rows: auto var(--topbar-height) 1fr;
			grid-template-areas:
				'sidebar'
				'topbar'
				'content';
		}

		.sidebar {
			border-right: none;
			border-bottom: 1px solid var(--color-border);
		}

		.sidebar ul {
			grid-auto-flow: column;
			overflow-x: auto;
		}
	}
</style>
