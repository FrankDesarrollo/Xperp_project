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
		Landmark,
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
		Menu,
		Sparkles,
		Lock
	} from '@lucide/svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import { auth } from '$lib/stores/auth.svelte';
	import { clock } from '$lib/stores/clock.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { notifications } from '$lib/stores/notifications.svelte';
	import { theme } from '$lib/stores/theme.svelte';
	import { approvals } from '$lib/stores/approvals.svelte';
	import { canAccess, sectionFor, type Section } from '$lib/utils/permissions';
	import { assistant, ASSISTANT_SUGGESTIONS } from '$lib/stores/assistant.svelte';
	import AssistantChat from '$lib/components/domain/AssistantChat.svelte';
	import Drawer from '$lib/components/ui/Drawer.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import { AI_MODE, ROLE_LABEL } from '$lib/utils/labels';
	import { formatDateTime } from '$lib/utils/format';
	import Badge from '$lib/components/ui/Badge.svelte';

	let { children } = $props();

	const groups = [
		{
			label: 'Operación',
			items: [
				{ href: '/tablero', label: 'Tablero', section: 'tablero', icon: Gauge },
				{ href: '/inventario', label: 'Inventario', section: 'inventario', icon: Boxes },
				{ href: '/ventas', label: 'Ventas', section: 'ventas', icon: Receipt },
				{ href: '/compras', label: 'Compras', section: 'compras', icon: ShoppingCart },
				{ href: '/finanzas', label: 'Finanzas', section: 'finanzas', icon: Landmark },
				{ href: '/bodega', label: 'Bodega', section: 'bodega', icon: Warehouse },
				{ href: '/despachos', label: 'Despachos', section: 'despachos', icon: Truck }
			]
		},
		{
			label: 'Inteligencia artificial',
			items: [
				{
					href: '/ia/aprobaciones',
					label: 'Aprobaciones',
					section: 'ia/aprobaciones',
					icon: ClipboardCheck
				},
				{ href: '/ia/decisiones', label: 'Decisiones', section: 'ia/decisiones', icon: ScrollText },
				{ href: '/ia/politicas', label: 'Políticas', section: 'ia/politicas', icon: ShieldCheck },
				{ href: '/asistente', label: 'Asistente', section: 'asistente', icon: MessageSquare }
			]
		},
		{
			label: 'Sistema',
			items: [
				{ href: '/simulacion', label: 'Simulación', section: 'simulacion', icon: Timer },
				{
					href: '/configuracion',
					label: 'Configuración',
					section: 'configuracion',
					icon: Settings
				}
			]
		}
	] as const;

	let ready = $state(false);
	let loadError = $state<string | null>(null);
	let showNotifications = $state(false);
	let showMenu = $state(false);
	let visible = $derived(
		groups
			.map((g) => ({
				label: g.label,
				items: g.items.filter((m) => canAccess(auth.role, m.section as Section))
			}))
			.filter((g) => g.items.length > 0)
	);
	let initials = $derived(
		(auth.user?.full_name ?? '')
			.split(' ')
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase())
			.join('')
	);
	let mode = $derived(AI_MODE[clock.aiMode] ?? { label: 'IA activa', tone: 'success' as const });
	// Una ruta de una sección que el rol no puede ver muestra «sin acceso» en vez de fallar a medias.
	let section = $derived(sectionFor(page.url.pathname));
	let allowed = $derived(section === null || canAccess(auth.role, section));
	let onAssistantPage = $derived(page.url.pathname.startsWith('/asistente'));
	let notif = $state<HTMLDivElement>();

	async function boot() {
		loadError = null;
		try {
			auth.setUser(await unwrap(api.GET('/auth/me')));
			clock.apply(await unwrap(api.GET('/simulation/clock')));
			await notifications.load();
			live.start(auth.token!);
			if (canAccess(auth.role, 'ia/aprobaciones')) approvals.start();
			ready = true;
		} catch (e) {
			// Solo un 401 cierra la sesión. Una petición abortada (el usuario se fue de la página)
			// o un backend caído no deben borrar el token.
			if (e instanceof ApiError && e.status === 401) {
				auth.clear();
				await goto(resolve('/login'));
			} else if (!(e instanceof DOMException && e.name === 'AbortError')) {
				loadError = 'No se pudo conectar con el servidor. Revisa la conexión e intenta de nuevo.';
			}
		}
	}

	function onkeydown(e: KeyboardEvent) {
		if (e.key !== 'Escape') return;
		showMenu = false;
		showNotifications = false;
	}

	function onpointerdown(e: PointerEvent) {
		if (showNotifications && notif && !notif.contains(e.target as Node)) {
			showNotifications = false;
		}
	}

	onMount(() => {
		if (!auth.token) {
			void goto(resolve('/login'));
			return;
		}
		void boot();
		// Por si el SSE se cae, el reloj se refresca cada 10 s.
		const timer = setInterval(async () => {
			if (!live.connected && auth.token) {
				const { data } = await api.GET('/simulation/clock');
				if (data) clock.apply(data);
			}
		}, 10_000);
		return () => {
			clearInterval(timer);
			approvals.stop();
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

<svelte:window {onkeydown} {onpointerdown} />

{#if ready}
	<div class="shell">
		{#if showMenu}
			<div class="backdrop" role="presentation" onclick={() => (showMenu = false)}></div>
		{/if}
		<nav id="main-menu" class="sidebar" class:open={showMenu} aria-label="Menú principal">
			<a class="brand" href={resolve('/tablero')}>
				<span class="logo" aria-hidden="true">
					<svg viewBox="0 0 24 24" width="18" height="18" fill="none">
						<path
							d="M5 5l14 14M19 5L5 19"
							stroke="currentColor"
							stroke-width="2.6"
							stroke-linecap="round"
						/>
					</svg>
				</span>
				<span class="brand-text">
					<strong>Xperp</strong>
					<small>Distribuidora La Ceiba</small>
				</span>
			</a>
			<div class="groups">
				{#each visible as group (group.label)}
					<div class="group">
						<span class="group-label">{group.label}</span>
						<ul>
							{#each group.items as item (item.href)}
								{@const Icon = item.icon}
								<li>
									<a
										href={resolve(item.href)}
										aria-current={page.url.pathname.startsWith(item.href) ? 'page' : undefined}
										onclick={() => (showMenu = false)}
									>
										<Icon size={17} strokeWidth={1.9} aria-hidden="true" />
										<span class="item-label">{item.label}</span>
										{#if item.href === '/ia/aprobaciones' && approvals.pending > 0}
											<span class="nav-count" aria-label="{approvals.pending} pendientes"
												>{approvals.pending}</span
											>
										{/if}
									</a>
								</li>
							{/each}
						</ul>
					</div>
				{/each}
			</div>
			<div class="user">
				<span class="avatar" aria-hidden="true">{initials}</span>
				<div class="who">
					<strong>{auth.user?.full_name}</strong>
					<span>{ROLE_LABEL[auth.user?.role ?? ''] ?? ''}</span>
				</div>
				<button class="icon" onclick={logout} aria-label="Cerrar sesión" title="Cerrar sesión">
					<LogOut size={17} />
				</button>
			</div>
		</nav>

		<header class="topbar">
			<button
				class="icon menu-toggle"
				onclick={() => (showMenu = !showMenu)}
				aria-label="Menú"
				aria-expanded={showMenu}
				aria-controls="main-menu"
			>
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
			<div class="notif" bind:this={notif}>
				<button
					class="icon"
					onclick={() => (showNotifications = !showNotifications)}
					aria-label="Notificaciones ({notifications.unread} sin leer)"
					aria-expanded={showNotifications}
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
			{#if allowed}
				{@render children()}
			{:else}
				<div class="denied card" role="alert">
					<Lock size={28} aria-hidden="true" />
					<h1>Esta sección no está disponible para tu rol</h1>
					<p class="text-muted">
						Con el rol {ROLE_LABEL[auth.user?.role ?? ''] ?? ''} no puedes ver esta pantalla. Si la necesitas,
						pídele acceso a un administrador.
					</p>
					<a href={resolve('/tablero')}>Volver al tablero</a>
				</div>
			{/if}
		</main>

		{#if !onAssistantPage}
			<button
				class="ask-ai"
				onclick={() => (assistant.open = true)}
				aria-haspopup="dialog"
				aria-label="Preguntar a la IA"
			>
				<Sparkles size={20} aria-hidden="true" />
				<span>Preguntar a la IA</span>
			</button>
		{/if}
		<Drawer
			open={assistant.open && !onAssistantPage}
			flush
			title="Asistente de Xperp"
			subtitle="Consulta datos y recomendaciones de la IA; no ejecuta acciones"
			onclose={() => (assistant.open = false)}
		>
			<AssistantChat suggestions={ASSISTANT_SUGGESTIONS} />
		</Drawer>
	</div>
{:else}
	<div class="loading text-muted" role={loadError ? 'alert' : 'status'}>
		<p>{loadError ?? 'Cargando Xperp…'}</p>
		{#if loadError}<Button variant="primary" onclick={boot}>Reintentar</Button>{/if}
	</div>
{/if}

<style>
	.shell {
		display: grid;
		grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
		grid-template-rows: var(--topbar-height) 1fr;
		grid-template-areas:
			'sidebar topbar'
			'sidebar content';
		min-height: 100dvh;
	}

	/* ---------- Menú lateral ---------- */
	.sidebar {
		grid-area: sidebar;
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		padding: var(--space-4) var(--space-3) var(--space-3);
		background: var(--color-surface);
		border-right: 1px solid var(--color-border);
		position: sticky;
		top: 0;
		height: 100dvh;
		overflow-y: auto;
		scrollbar-width: thin;
	}

	.brand {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-1) var(--space-2);
		color: var(--color-text);
		text-decoration: none;
	}

	.brand-text {
		display: grid;
		line-height: 1.2;
	}

	.brand-text strong {
		font-size: var(--text-md);
		letter-spacing: var(--tracking-tight);
	}

	.brand-text small {
		font-size: var(--text-xs);
		color: var(--color-text-muted);
	}

	.logo {
		display: grid;
		place-items: center;
		width: 2rem;
		height: 2rem;
		border-radius: var(--radius-md);
		background: linear-gradient(140deg, var(--color-primary), var(--color-primary-hover));
		color: var(--color-on-primary);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.18),
			var(--shadow-sm);
	}

	.groups {
		display: grid;
		gap: var(--space-5);
		flex: 1;
		align-content: start;
	}

	.group-label {
		display: block;
		padding: 0 var(--space-3) var(--space-2);
		font-size: 0.6875rem;
		font-weight: var(--weight-semibold);
		letter-spacing: var(--tracking-wide);
		text-transform: uppercase;
		color: var(--color-text-subtle);
	}

	.sidebar ul {
		display: grid;
		gap: 1px;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.sidebar ul a[href] {
		position: relative;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: 0.4375rem var(--space-3);
		border-radius: var(--radius-md);
		color: var(--color-text-muted);
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
		text-decoration: none;
		transition:
			background var(--duration-fast) var(--ease-out),
			color var(--duration-fast) var(--ease-out);
	}

	.sidebar ul a[href]:hover {
		background: var(--color-surface-muted);
		color: var(--color-text);
	}

	.sidebar ul a[aria-current='page'] {
		background: var(--color-surface-muted);
		color: var(--color-text);
	}

	.sidebar ul a[aria-current='page']::before {
		content: '';
		position: absolute;
		left: calc(var(--space-3) * -1 + 1px);
		top: 0.4rem;
		bottom: 0.4rem;
		width: 2px;
		border-radius: 0 2px 2px 0;
		background: var(--color-primary);
	}

	.sidebar ul a[aria-current='page'] :global(svg) {
		color: var(--color-primary);
	}

	.item-label {
		flex: 1;
	}

	.nav-count {
		min-width: 1.25rem;
		padding: 0 0.375rem;
		border-radius: var(--radius-full);
		background: var(--color-warning-bg);
		color: var(--color-warning);
		font-size: 0.6875rem;
		font-weight: var(--weight-semibold);
		line-height: 1.25rem;
		text-align: center;
		font-variant-numeric: tabular-nums;
	}

	.user {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-2) var(--space-1);
		border-top: 1px solid var(--color-border-subtle);
	}

	.avatar {
		flex: none;
		display: grid;
		place-items: center;
		width: 2rem;
		height: 2rem;
		border-radius: var(--radius-full);
		background: var(--color-primary-soft);
		color: var(--color-primary);
		font-size: var(--text-xs);
		font-weight: var(--weight-semibold);
	}

	.who {
		display: grid;
		flex: 1;
		min-width: 0;
		line-height: 1.3;
	}

	.who strong {
		font-size: var(--text-sm);
		font-weight: var(--weight-semibold);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.who span {
		font-size: var(--text-xs);
		color: var(--color-text-muted);
	}

	/* ---------- Barra superior ---------- */
	.topbar {
		grid-area: topbar;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: 0 var(--space-6);
		background: color-mix(in srgb, var(--color-bg) 82%, transparent);
		backdrop-filter: saturate(160%) blur(10px);
		-webkit-backdrop-filter: saturate(160%) blur(10px);
		border-bottom: 1px solid var(--color-border);
		position: sticky;
		top: 0;
		z-index: var(--z-sticky);
	}

	.clock {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: 0.25rem var(--space-2) 0.25rem var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-full);
		background: var(--color-surface);
		color: var(--color-text-muted);
		font-size: var(--text-sm);
	}

	.clock .mono {
		color: var(--color-text);
		font-weight: var(--weight-medium);
	}

	.live {
		display: inline-flex;
		align-items: center;
		gap: 0.375rem;
		color: var(--color-text-muted);
	}

	.live .dot {
		width: 7px;
		height: 7px;
		border-radius: var(--radius-full);
		background: var(--color-warning);
	}

	.live.on .dot {
		background: var(--color-success);
		box-shadow: 0 0 0 3px var(--color-success-bg);
	}

	.icon {
		position: relative;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 2.25rem;
		height: 2.25rem;
		padding: 0;
		border: none;
		border-radius: var(--radius-md);
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
		transition:
			background var(--duration-fast) var(--ease-out),
			color var(--duration-fast) var(--ease-out);
	}

	.icon:hover {
		background: var(--color-surface-muted);
		color: var(--color-text);
	}

	.menu-toggle {
		display: none;
	}

	.backdrop {
		display: none;
	}

	.notif {
		position: relative;
	}

	.count {
		position: absolute;
		top: 3px;
		right: 2px;
		min-width: 16px;
		padding: 0 4px;
		border-radius: var(--radius-full);
		background: var(--color-danger);
		color: var(--color-surface);
		font-size: 0.625rem;
		font-weight: var(--weight-semibold);
		line-height: 16px;
		text-align: center;
		box-shadow: 0 0 0 2px var(--color-bg);
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
		animation: pop var(--duration-base) var(--ease-out);
		transform-origin: top right;
	}

	.panel-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: var(--space-3) var(--space-4);
		border-bottom: 1px solid var(--color-border-subtle);
	}

	.panel ul {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.panel li button {
		display: grid;
		gap: 2px;
		width: 100%;
		padding: var(--space-3) var(--space-4);
		border: none;
		border-bottom: 1px solid var(--color-border-subtle);
		background: transparent;
		text-align: left;
		cursor: pointer;
		transition: background var(--duration-fast) var(--ease-out);
	}

	.panel li button:hover {
		background: var(--color-surface-hover);
	}

	.panel li.unread button {
		background: var(--color-primary-soft);
	}

	.pad {
		margin: 0;
		padding: var(--space-4);
	}

	.link {
		border: none;
		background: none;
		color: var(--color-primary);
		font-weight: var(--weight-medium);
		cursor: pointer;
	}

	/* ---------- Contenido ---------- */
	.content {
		grid-area: content;
		width: 100%;
		max-width: 1480px;
		min-width: 0;
		margin-inline: auto;
		padding: var(--space-6);
		/* Espacio para que el botón «Preguntar a la IA» no tape el final de la página */
		padding-bottom: calc(var(--space-7) + var(--space-6));
	}

	.denied {
		display: grid;
		justify-items: start;
		gap: var(--space-3);
		max-width: 560px;
	}

	.denied h1 {
		margin: 0;
		font-size: var(--text-xl);
	}

	.denied p {
		margin: 0;
	}

	.ask-ai {
		position: fixed;
		right: var(--space-5);
		bottom: var(--space-5);
		z-index: var(--z-sticky);
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: 0.625rem var(--space-4) 0.625rem var(--space-3);
		border: 1px solid color-mix(in srgb, var(--color-ai) 30%, var(--color-border));
		border-radius: var(--radius-full);
		background: var(--color-surface);
		color: var(--color-ai);
		font-size: var(--text-sm);
		font-weight: var(--weight-semibold);
		box-shadow: var(--shadow-lg);
		cursor: pointer;
		transition:
			transform var(--duration-base) var(--ease-out),
			background var(--duration-base) var(--ease-out);
	}

	.ask-ai:hover {
		transform: translateY(-2px);
		background: var(--color-ai-bg);
	}

	.loading {
		display: grid;
		place-content: center;
		justify-items: center;
		gap: var(--space-3);
		min-height: 100dvh;
		padding: var(--space-4);
		text-align: center;
	}

	.loading p {
		margin: 0;
	}

	@keyframes pop {
		from {
			opacity: 0;
			transform: scale(0.97) translateY(-4px);
		}
	}

	@media (max-width: 900px) {
		.shell {
			grid-template-columns: minmax(0, 1fr);
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
			/* Oculto también para el teclado y el lector de pantalla mientras está cerrado */
			visibility: hidden;
			transition:
				transform var(--duration-base) var(--ease-out),
				visibility 0s linear var(--duration-base);
			box-shadow: var(--shadow-lg);
		}

		.sidebar.open {
			transform: none;
			visibility: visible;
			transition: transform var(--duration-base) var(--ease-out);
		}

		.sidebar ul a[href] {
			min-height: 2.75rem;
		}

		.backdrop {
			display: block;
			position: fixed;
			inset: 0;
			z-index: calc(var(--z-drawer) - 1);
			background: var(--color-overlay);
		}

		.icon {
			width: 2.75rem;
			height: 2.75rem;
		}

		.menu-toggle {
			display: inline-flex;
		}

		.topbar {
			min-width: 0;
			padding: 0 var(--space-3);
			gap: var(--space-2);
		}

		.live span.small,
		.clock .small {
			display: none;
		}

		.content {
			overflow-x: hidden;
			padding: var(--space-5) var(--space-4);
			padding-bottom: calc(var(--space-7) + var(--space-5));
		}
	}

	@media (max-width: 600px) {
		.hide-sm {
			display: none;
		}

		.ask-ai {
			right: var(--space-4);
			bottom: var(--space-4);
			padding: var(--space-3);
		}

		.ask-ai span {
			display: none;
		}

		.clock :global(.badge) {
			display: none;
		}

		.content {
			padding: var(--space-4);
			padding-bottom: calc(var(--space-7) + var(--space-5));
		}
	}
</style>
