<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import {
		Bell,
		Bot,
		Boxes,
		ClipboardCheck,
		Gauge,
		LogOut,
		MessageSquare,
		Moon,
		ScrollText,
		Settings,
		ShieldCheck,
		ShoppingCart,
		SlidersHorizontal,
		Sun,
		SunMoon,
		Timer,
		Truck,
		Warehouse,
		Receipt,
		Menu
	} from '@lucide/svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import { auth } from '$lib/stores/auth.svelte';
	import { clock } from '$lib/stores/clock.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { notifications } from '$lib/stores/notifications.svelte';
	import { theme } from '$lib/stores/theme.svelte';
	import { canAccess, type Section } from '$lib/utils/permissions';
	import { AI_MODE, ROLE_LABEL } from '$lib/utils/labels';
	import { formatDateTime } from '$lib/utils/format';
	import Badge from '$lib/components/ui/Badge.svelte';

	let { children } = $props();

	const menu = [
		{ href: '/tablero', label: 'Tablero', section: 'tablero', icon: Gauge },
		{ href: '/inventario', label: 'Inventario', section: 'inventario', icon: Boxes },
		{ href: '/ventas', label: 'Ventas', section: 'ventas', icon: Receipt },
		{ href: '/compras', label: 'Compras', section: 'compras', icon: ShoppingCart },
		{ href: '/bodega', label: 'Bodega', section: 'bodega', icon: Warehouse },
		{ href: '/despachos', label: 'Despachos', section: 'despachos', icon: Truck },
		{
			href: '/ia/decisiones',
			label: 'IA · Decisiones',
			section: 'ia/decisiones',
			icon: ScrollText
		},
		{
			href: '/ia/aprobaciones',
			label: 'IA · Aprobaciones',
			section: 'ia/aprobaciones',
			icon: ClipboardCheck
		},
		{ href: '/ia/politicas', label: 'IA · Políticas', section: 'ia/politicas', icon: ShieldCheck },
		{ href: '/asistente', label: 'Asistente', section: 'asistente', icon: MessageSquare },
		{ href: '/simulacion', label: 'Simulación', section: 'simulacion', icon: Timer },
		{ href: '/configuracion', label: 'Configuración', section: 'configuracion', icon: Settings }
	] as const;

	let ready = $state(false);
	let loadError = $state<string | null>(null);
	let showNotifications = $state(false);
	let showMenu = $state(false);
	let visible = $derived(menu.filter((m) => canAccess(auth.role, m.section as Section)));
	let mode = $derived(AI_MODE[clock.aiMode] ?? { label: 'IA activa', tone: 'success' as const });

	onMount(() => {
		if (!auth.token) {
			void goto(resolve('/login'));
			return;
		}
		void (async () => {
			try {
				auth.setUser(await unwrap(api.GET('/auth/me')));
				clock.apply(await unwrap(api.GET('/simulation/clock')));
				await notifications.load();
				live.start(auth.token!);
				ready = true;
			} catch (e) {
				// Solo un 401 cierra la sesión. Una petición abortada (el usuario se fue de la página)
				// o un backend caído no deben borrar el token.
				if (e instanceof ApiError && e.status === 401) {
					auth.clear();
					await goto(resolve('/login'));
				} else if (!(e instanceof DOMException && e.name === 'AbortError')) {
					loadError = 'No se pudo conectar con el servidor. Reintenta en unos segundos.';
				}
			}
		})();
		// Por si el SSE se cae, el reloj se refresca cada 10 s.
		const timer = setInterval(async () => {
			if (!live.connected && auth.token) {
				const { data } = await api.GET('/simulation/clock');
				if (data) clock.apply(data);
			}
		}, 10_000);
		return () => {
			clearInterval(timer);
			live.stop();
		};
	});

	$effect(() => {
		if (ready && !auth.token) {
			live.stop();
			void goto(resolve('/login'));
		}
	});

	function logout() {
		live.stop();
		auth.clear();
		void goto(resolve('/login'));
	}

	async function openNotification(id: number, link: string | null | undefined) {
		await notifications.markRead(id);
		showNotifications = false;
		// eslint-disable-next-line svelte/no-navigation-without-resolve -- el backend arma el enlace con la ruta de la app
		if (link) void goto(link);
	}
</script>

{#if ready}
	<div class="shell">
		<nav class="sidebar" class:open={showMenu} aria-label="Menú principal">
			<a class="brand" href={resolve('/tablero')}>
				<span class="logo" aria-hidden="true">X</span>
				<span>
					<strong>Xperp</strong>
					<small class="text-muted">La Ceiba S.A.S.</small>
				</span>
			</a>
			<ul>
				{#each visible as item (item.href)}
					{@const Icon = item.icon}
					<li>
						<a
							href={resolve(item.href)}
							aria-current={page.url.pathname.startsWith(item.href) ? 'page' : undefined}
							onclick={() => (showMenu = false)}
						>
							<Icon size={18} />
							{item.label}
						</a>
					</li>
				{/each}
			</ul>
			<div class="user">
				<div>
					<strong class="small">{auth.user?.full_name}</strong>
					<span class="text-muted small">{ROLE_LABEL[auth.user?.role ?? ''] ?? ''}</span>
				</div>
				<button class="icon" onclick={logout} aria-label="Cerrar sesión" title="Cerrar sesión">
					<LogOut size={18} />
				</button>
			</div>
		</nav>

		<header class="topbar">
			<button class="icon menu-toggle" onclick={() => (showMenu = !showMenu)} aria-label="Menú">
				<Menu size={20} />
			</button>
			<div class="clock" title="Hora simulada">
				<Timer size={16} />
				<span class="mono">{formatDateTime(clock.simTime)}</span>
				<span class="text-muted small">día {clock.dayNumber}</span>
				<Badge tone={clock.status === 'running' ? 'success' : 'neutral'}>
					{clock.status === 'running' ? 'En marcha' : 'En pausa'}
				</Badge>
			</div>
			<span class="spacer"></span>
			<span class="hide-sm"><Badge tone={mode.tone}><Bot size={12} />{mode.label}</Badge></span>
			<span
				class="live"
				class:on={live.connected}
				title={live.connected ? 'En vivo' : 'Sin conexión en vivo'}
			>
				<span class="dot" aria-hidden="true"></span>
				<span class="small">{live.connected ? 'En vivo' : 'Reconectando'}</span>
			</span>
			<button
				class="icon hide-sm"
				onclick={() => theme.cycle()}
				aria-label="Cambiar tema"
				title="Tema: {theme.choice}"
			>
				{#if theme.choice === 'dark'}<Moon size={18} />{:else if theme.choice === 'light'}<Sun
						size={18}
					/>{:else}<SunMoon size={18} />{/if}
			</button>
			<div class="notif">
				<button
					class="icon"
					onclick={() => (showNotifications = !showNotifications)}
					aria-label="Notificaciones ({notifications.unread} sin leer)"
				>
					<Bell size={18} />
					{#if notifications.unread > 0}<span class="count"
							>{notifications.unread > 99 ? '99+' : notifications.unread}</span
						>{/if}
				</button>
				{#if showNotifications}
					<div class="panel" role="dialog" aria-label="Notificaciones">
						<div class="panel-head">
							<strong>Notificaciones</strong>
							<button class="link small" onclick={() => notifications.markAllRead()}
								>Marcar todas</button
							>
						</div>
						{#if notifications.items.length === 0}
							<p class="text-muted small pad">No tienes notificaciones.</p>
						{:else}
							<ul>
								{#each notifications.items as n (n.id)}
									<li class:unread={!n.read_at}>
										<button onclick={() => openNotification(n.id, n.link)}>
											<strong class="small">{n.title}</strong>
											<span class="small text-muted">{n.body}</span>
											<span class="small text-muted">{formatDateTime(n.created_at)}</span>
										</button>
									</li>
								{/each}
							</ul>
						{/if}
					</div>
				{/if}
			</div>
			<a
				class="icon hide-sm"
				href={resolve('/ia/politicas')}
				aria-label="Controles de la IA"
				title="Controles de la IA"
			>
				<SlidersHorizontal size={18} />
			</a>
		</header>

		<main class="content">
			{@render children()}
		</main>
	</div>
{:else}
	<div class="loading text-muted">{loadError ?? 'Cargando Xperp…'}</div>
{/if}

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
		padding: var(--space-4) var(--space-3);
		background: var(--color-surface);
		border-right: 1px solid var(--color-border);
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
		position: sticky;
		top: 0;
		height: 100dvh;
		overflow-y: auto;
	}

	.brand {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		text-decoration: none;
		color: var(--color-text);
		padding: 0 var(--space-2);
	}

	.brand span:last-child {
		display: grid;
	}

	.logo {
		width: 2.25rem;
		height: 2.25rem;
		border-radius: var(--radius-md);
		background: var(--color-primary);
		color: var(--color-on-primary);
		display: grid;
		place-items: center;
		font-weight: var(--weight-bold);
	}

	.sidebar ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
		flex: 1;
		align-content: start;
	}

	.sidebar a[href] {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-md);
		color: var(--color-text);
		text-decoration: none;
		font-size: var(--text-sm);
	}

	.sidebar ul a:hover {
		background: var(--color-surface-muted);
	}

	.sidebar ul a[aria-current='page'] {
		background: var(--color-primary);
		color: var(--color-on-primary);
	}

	.user {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-2) 0;
		border-top: 1px solid var(--color-border);
	}

	.user div {
		display: grid;
	}

	.topbar {
		grid-area: topbar;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: 0 var(--space-5);
		background: var(--color-surface);
		border-bottom: 1px solid var(--color-border);
		position: sticky;
		top: 0;
		z-index: var(--z-sticky);
	}

	.clock {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.live {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		color: var(--color-text-muted);
	}

	.live .dot {
		width: 8px;
		height: 8px;
		border-radius: var(--radius-full);
		background: var(--color-warning);
	}

	.live.on .dot {
		background: var(--color-success);
	}

	.icon {
		border: none;
		background: transparent;
		color: var(--color-text-muted);
		padding: var(--space-2);
		border-radius: var(--radius-md);
		cursor: pointer;
		display: inline-flex;
		position: relative;
	}

	.icon:hover {
		background: var(--color-surface-muted);
		color: var(--color-text);
	}

	.menu-toggle {
		display: none;
	}

	.notif {
		position: relative;
	}

	.count {
		position: absolute;
		top: 0;
		right: 0;
		background: var(--color-danger);
		color: var(--color-surface);
		font-size: 0.625rem;
		border-radius: var(--radius-full);
		padding: 0 4px;
		min-width: 16px;
		text-align: center;
	}

	.panel {
		position: absolute;
		right: 0;
		top: calc(100% + var(--space-2));
		width: min(380px, 90vw);
		max-height: 70vh;
		overflow-y: auto;
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-lg);
	}

	.panel-head {
		display: flex;
		justify-content: space-between;
		padding: var(--space-3) var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}

	.panel ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.panel li button {
		width: 100%;
		text-align: left;
		display: grid;
		gap: 2px;
		padding: var(--space-3) var(--space-4);
		border: none;
		border-bottom: 1px solid var(--color-border);
		background: transparent;
		cursor: pointer;
	}

	.panel li.unread button {
		background: var(--color-info-bg);
	}

	.pad {
		padding: var(--space-4);
		margin: 0;
	}

	.link {
		border: none;
		background: none;
		color: var(--color-primary);
		cursor: pointer;
	}

	.content {
		grid-area: content;
		padding: var(--space-5) var(--space-6);
		min-width: 0;
		max-width: 1440px;
		width: 100%;
	}

	.loading {
		min-height: 100dvh;
		display: grid;
		place-items: center;
	}

	@media (max-width: 900px) {
		.shell {
			grid-template-columns: 1fr;
			grid-template-areas:
				'topbar'
				'content';
		}

		.sidebar {
			position: fixed;
			left: 0;
			top: 0;
			width: min(280px, 85vw);
			z-index: var(--z-drawer);
			transform: translateX(-100%);
			transition: transform var(--duration-base);
			box-shadow: var(--shadow-lg);
		}

		.sidebar.open {
			transform: none;
		}

		.menu-toggle {
			display: inline-flex;
		}

		.topbar {
			padding: 0 var(--space-3);
			gap: var(--space-2);
		}

		.live span.small,
		.clock .small {
			display: none;
		}

		.topbar {
			min-width: 0;
		}

		.content {
			min-width: 0;
			overflow-x: hidden;
		}
	}

	@media (max-width: 600px) {
		.hide-sm {
			display: none;
		}

		.clock :global(.badge) {
			display: none;
		}

		.content {
			padding: var(--space-4);
		}
	}
</style>
