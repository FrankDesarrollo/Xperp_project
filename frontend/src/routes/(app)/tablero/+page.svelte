<script lang="ts">
	import { onMount } from 'svelte';
	import { fly } from 'svelte/transition';
	import { flip } from 'svelte/animate';
	import { cubicOut } from 'svelte/easing';
	import { resolve } from '$app/paths';
	import {
		ArrowRight,
		Boxes,
		CircleCheck,
		ClipboardCheck,
		Gauge,
		PackageX,
		TrendingUp,
		Truck,
		Wallet
	} from '@lucide/svelte';
	import { api, unwrap } from '$lib/api/client';
	import type {
		AiDecision,
		Alert,
		Budget,
		InventoryItem,
		Kpis,
		Series,
		StockRecommendation
	} from '$lib/api/types';
	import { live } from '$lib/stores/live.svelte';
	import { liveRefresh } from '$lib/utils/live-refresh';
	import { clock } from '$lib/stores/clock.svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { approvals } from '$lib/stores/approvals.svelte';
	import { canAccess } from '$lib/utils/permissions';
	import MetricCard from '$lib/components/domain/MetricCard.svelte';
	import AnimatedNumber from '$lib/components/ui/AnimatedNumber.svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import EChart from '$lib/components/charts/EChart.svelte';
	import { lineOption } from '$lib/components/charts/options';
	import { Loader } from '$lib/utils/loader.svelte';
	import {
		formatCop,
		formatCopShort,
		formatDateTime,
		formatNumber,
		formatPct,
		formatTime
	} from '$lib/utils/format';
	import {
		DECISION_STATUS,
		DECISION_TYPE,
		RECOMMENDATION_KIND,
		RECOMMENDATION_PRIORITY,
		SALES_STATUS,
		SEVERITY,
		STOCK_STATUS,
		type Tone
	} from '$lib/utils/labels';

	interface LiveSale {
		id: number;
		number: string;
		customer_name: string;
		total: string;
		status: string;
		ordered_at: string;
	}

	interface AlertGroup {
		type: string;
		label: string;
		count: number;
		tone: Tone;
		latest: Alert;
	}

	let kpis = $state<Kpis | null>(null);
	let sales = $state<Series | null>(null);
	let service = $state<Series | null>(null);
	let budgets = $state<Budget[]>([]);
	let stockCounts = $state<Record<string, number>>({});
	let decisions = $state<AiDecision[]>([]);
	let alerts = $state<Alert[]>([]);
	let feed = $state<LiveSale[]>([]);
	let recs = $state<StockRecommendation[]>([]);
	const loader = new Loader();

	/** La gráfica grande se anima solo la primera vez; las recargas en vivo no la redibujan. */
	let salesChartBuilt = false;

	const STOCK_ORDER = ['stockout', 'low', 'ok', 'over'] as const;
	const STOCK_COLOR: Record<string, string> = {
		stockout: 'var(--color-danger)',
		low: 'var(--color-warning)',
		ok: 'var(--color-success)',
		over: 'var(--color-info)'
	};

	let urgent = $derived(
		[...recs]
			.filter((r) => r.priority !== 'low')
			.sort((a, b) => (a.priority === 'high' ? -1 : 0) - (b.priority === 'high' ? -1 : 0))
	);
	/** Solo días cerrados: el día en curso aún no termina y haría caer la curva al final. */
	let salesClosed = $derived(
		sales?.points.filter((p) => !clock.today || p.date < clock.today) ?? []
	);
	let serviceClosed = $derived(
		service?.points.filter((p) => !clock.today || p.date < clock.today) ?? []
	);
	let serviceFloor = $derived(Math.min(0.9, ...serviceClosed.map((p) => p.value)));
	let stockTotal = $derived(STOCK_ORDER.reduce((n, s) => n + (stockCounts[s] ?? 0), 0));
	let canApprove = $derived(canAccess(auth.role, 'ia/aprobaciones'));
	let firstName = $derived(auth.user?.full_name?.split(' ')[0] ?? '');

	let alertGroups = $derived.by<AlertGroup[]>(() => {
		const groups: Record<string, AlertGroup> = {};
		for (const a of alerts) {
			const tone = SEVERITY[a.severity] ?? 'info';
			const g = groups[a.type];
			if (!g) {
				groups[a.type] = {
					type: a.type,
					label: DECISION_TYPE[a.type] ?? a.type,
					count: 1,
					tone,
					latest: a
				};
			} else {
				g.count++;
				if (tone === 'danger') g.tone = 'danger';
			}
		}
		return Object.values(groups);
	});

	interface AttentionItem {
		route: '/ia/aprobaciones' | '/inventario' | '/compras';
		query: string;
		text: string;
		tone: Tone;
		icon: typeof ClipboardCheck;
	}

	let attention = $derived.by(() => {
		if (!kpis) return [];
		const items: AttentionItem[] = [];
		if (approvals.pending > 0 && canApprove) {
			const n = approvals.pending;
			items.push({
				route: '/ia/aprobaciones',
				query: '',
				text: `${n} ${n === 1 ? 'recomendación espera' : 'recomendaciones esperan'} tu aprobación`,
				tone: 'warning',
				icon: ClipboardCheck
			});
		}
		if (kpis.stockout_skus > 0) {
			items.push({
				route: '/inventario',
				query: '?status=stockout',
				text: `${kpis.stockout_skus} ${kpis.stockout_skus === 1 ? 'existencia en quiebre' : 'existencias en quiebre'}`,
				tone: 'danger',
				icon: PackageX
			});
		}
		if (kpis.late_purchase_orders > 0) {
			items.push({
				route: '/compras',
				query: '',
				text: `${kpis.late_purchase_orders} ${kpis.late_purchase_orders === 1 ? 'OC atrasada' : 'OC atrasadas'}`,
				tone: 'warning',
				icon: Truck
			});
		}
		return items;
	});

	function serviceStatus(v: number): { label: string; tone: Tone } {
		if (v >= 0.97) return { label: 'En meta', tone: 'success' };
		if (v >= 0.93) return { label: 'Atención', tone: 'warning' };
		return { label: 'Crítico', tone: 'danger' };
	}

	function budgetTone(pct: number): string {
		if (pct > 85) return 'var(--color-danger)';
		if (pct > 70) return 'var(--color-warning)';
		return 'var(--color-primary)';
	}

	async function load() {
		const [k, s, sl, b, inv, d, a, r] = await Promise.all([
			unwrap(api.GET('/dashboard/kpis')),
			unwrap(api.GET('/dashboard/series', { params: { query: { metric: 'sales' } } })),
			unwrap(api.GET('/dashboard/series', { params: { query: { metric: 'service_level' } } })),
			unwrap(api.GET('/budgets')),
			unwrap(api.GET('/inventory', { params: { query: { size: 200 } } })),
			unwrap(
				api.GET('/ai/decisions', {
					params: { query: { size: 30, exclude_type: 'fulfillment' } }
				})
			),
			unwrap(api.GET('/ai/alerts', { params: { query: { limit: 20 } } })),
			unwrap(api.GET('/ai/recommendations'))
		]);
		kpis = k;
		sales = s;
		service = sl;
		budgets = b;
		const counts: Record<string, number> = { stockout: 0, low: 0, ok: 0, over: 0 };
		inv.items.forEach((i: InventoryItem) => (counts[i.status] = (counts[i.status] ?? 0) + 1));
		stockCounts = counts;
		decisions = d.items.filter((x) => x.decision_type !== 'carrier_selection').slice(0, 6);
		alerts = a;
		recs = r;
	}

	onMount(() => {
		void loader.run(load);
		// Los indicadores se recalculan como mucho cada 5 s y nunca con la pestaña oculta.
		const offRefresh = liveRefresh(
			['ai.decision', 'alert.created', 'clock.tick'],
			() => void loader.run(load),
			{ delay: 5000 }
		);
		const offFeed = live.on('sale.created', (data) => {
			feed = [data as LiveSale, ...feed].slice(0, 7);
		});
		return () => {
			offRefresh();
			offFeed();
		};
	});
</script>

<svelte:head><title>Tablero · Xperp</title></svelte:head>

<header class="hero reveal" style:--i="0">
	<div>
		<span class="eyebrow">Distribuidora La Ceiba S.A.S.</span>
		<h1>{firstName ? `Hola, ${firstName}` : 'Tablero'}</h1>
		<p>
			Resumen de la operación al {clock.simTime ? formatDateTime(clock.simTime) : '—'} · día {clock.dayNumber}
			de la simulación
		</p>
	</div>
	{#if canApprove && approvals.pending > 0}
		<a class="cta" href={resolve('/ia/aprobaciones')}>
			<ClipboardCheck size={16} aria-hidden="true" />
			Revisar aprobaciones
			<span class="count tabular">{approvals.pending}</span>
		</a>
	{/if}
</header>

<LoadState {loader} what="los indicadores" onretry={() => loader.run(load)}>
	{#if kpis}
		<div class="board">
			<!-- Lo que requiere acción, primero -->
			<section class="attention reveal" style:--i="1" aria-label="Requiere atención">
				{#if attention.length > 0}
					<span class="eyebrow">Requiere atención</span>
					<ul>
						{#each attention as item (item.route)}
							{@const Icon = item.icon}
							<li>
								<a class="chip {item.tone}" href="{resolve(item.route)}{item.query}">
									<Icon size={15} aria-hidden="true" />
									{item.text}
									<ArrowRight size={14} aria-hidden="true" class="go" />
								</a>
							</li>
						{/each}
					</ul>
				{:else}
					<p class="ok">
						<CircleCheck size={16} aria-hidden="true" />
						Todo en orden: no hay aprobaciones pendientes, quiebres de inventario ni OC atrasadas.
					</p>
				{/if}
			</section>

			<!-- Indicadores principales -->
			<section class="metrics" aria-label="Indicadores principales">
				<div class="reveal" style:--i="2">
					<MetricCard
						label="Ventas del mes"
						icon={TrendingUp}
						value={Number(kpis.sales_period)}
						format={formatCopShort}
						hint="Hoy {formatCopShort(kpis.sales_today)} en {formatNumber(
							kpis.orders_today
						)} pedidos"
						trend={salesClosed.map((p) => p.value)}
						trendMin={0}
						href={resolve('/ventas')}
					/>
				</div>
				<div class="reveal" style:--i="3">
					<MetricCard
						label="Nivel de servicio"
						icon={Gauge}
						value={kpis.service_level}
						format={(v) => formatPct(v, true)}
						status={serviceStatus(kpis.service_level)}
						hint="{formatNumber(kpis.lost_sales_units)} und perdidas · {formatCopShort(
							kpis.lost_sales_amount
						)}"
						trend={serviceClosed.map((p) => p.value)}
						trendMin={serviceFloor}
					/>
				</div>
				<div class="reveal" style:--i="4">
					<MetricCard
						label="Existencias sanas"
						icon={Boxes}
						value={stockTotal ? ((stockCounts.ok ?? 0) / stockTotal) * 100 : 0}
						format={(v) => formatPct(v)}
						status={kpis.stockout_skus === 0
							? { label: 'Sin quiebres', tone: 'success' }
							: { label: `${kpis.stockout_skus} en quiebre`, tone: 'danger' }}
						hint="{kpis.low_stock_skus} en nivel bajo de {stockTotal} existencias"
						progress={stockTotal ? ((stockCounts.ok ?? 0) / stockTotal) * 100 : 0}
						progressTone="success"
						href={resolve('/inventario')}
					/>
				</div>
				<div class="reveal" style:--i="5">
					<MetricCard
						label="Presupuesto de compras"
						icon={Wallet}
						value={kpis.budget_execution_pct}
						format={(v) => formatPct(v)}
						status={kpis.budget_execution_pct > 85
							? { label: 'Sobre el 85 %', tone: 'warning' }
							: { label: 'Dentro del plan', tone: 'success' }}
						hint="{formatCopShort(kpis.purchases_period)} comprometidos en el mes"
						progress={kpis.budget_execution_pct}
						threshold={85}
					/>
				</div>
			</section>

			<!-- Indicadores secundarios en una sola franja -->
			<section class="strip card reveal" style:--i="6" aria-label="Otros indicadores">
				<div>
					<span class="eyebrow">Margen bruto</span>
					<strong
						><AnimatedNumber value={kpis.gross_margin_pct} format={(v) => formatPct(v)} /></strong
					>
					<span class="sub">{formatCopShort(kpis.gross_margin)} en el mes</span>
				</div>
				<div>
					<span class="eyebrow">OC abiertas</span>
					<strong
						><AnimatedNumber
							value={kpis.open_purchase_orders}
							format={(v) => formatNumber(v)}
						/></strong
					>
					<span class="sub" class:warn={kpis.late_purchase_orders > 0}
						>{kpis.late_purchase_orders} atrasadas</span
					>
				</div>
				<div>
					<span class="eyebrow">Decisiones de la IA</span>
					<strong
						><AnimatedNumber
							value={kpis.decisions.executed + kpis.decisions.approved}
							format={(v) => formatNumber(v)}
						/></strong
					>
					<span class="sub"
						>{kpis.decisions.pending_approval} por aprobar · {kpis.decisions.blocked} bloqueadas</span
					>
				</div>
				<div>
					<span class="eyebrow">Valor del inventario</span>
					<strong
						><AnimatedNumber value={Number(kpis.inventory_value)} format={formatCopShort} /></strong
					>
					<span class="sub">Rotación {formatNumber(kpis.rotation, 1)}</span>
				</div>
				<div>
					<span class="eyebrow">Sobrecosto asumido</span>
					<strong
						><AnimatedNumber
							value={Number(kpis.overcost_assumed)}
							format={formatCopShort}
						/></strong
					>
					<span class="sub">
						{kpis.avg_approval_minutes
							? `Aprobación promedio ${formatNumber(kpis.avg_approval_minutes / 60, 1)} h`
							: 'Por reasignaciones de OC'}
					</span>
				</div>
			</section>

			<!-- Tendencia y salud del inventario -->
			<section class="split reveal" style:--i="7">
				<div class="card">
					<div class="card-head">
						<div>
							<span class="eyebrow">Ventas diarias</span>
							<h2>Últimos 30 días</h2>
						</div>
					</div>
					{#if sales}
						<EChart
							label="Ventas diarias de los últimos 30 días"
							height="260px"
							option={(t) => {
								const o = lineOption(
									t,
									[{ name: 'Ventas', points: salesClosed, area: true }],
									'money',
									{ animate: !salesChartBuilt, smooth: true }
								);
								salesChartBuilt = true;
								return o;
							}}
						/>
					{/if}
				</div>

				<div class="card">
					<div class="card-head">
						<div>
							<span class="eyebrow">Inventario</span>
							<h2>Estado de las existencias</h2>
						</div>
						<a class="more" href={resolve('/inventario')}>Ver inventario</a>
					</div>
					<div class="segments" role="img" aria-label="Distribución de existencias por estado">
						{#each STOCK_ORDER as s, i (s)}
							{#if (stockCounts[s] ?? 0) > 0}
								<span
									style:flex-grow={stockCounts[s]}
									style:background={STOCK_COLOR[s]}
									style:--d="{i * 90}ms"
								></span>
							{/if}
						{/each}
					</div>
					<ul class="legend">
						{#each STOCK_ORDER as s (s)}
							<li>
								<a href="{resolve('/inventario')}?status={s}">
									<span class="swatch" style:background={STOCK_COLOR[s]}></span>
									<span>{STOCK_STATUS[s]?.label ?? s}</span>
									<span class="spacer"></span>
									<strong class="tabular">{stockCounts[s] ?? 0}</strong>
									<span class="pct tabular"
										>{stockTotal
											? formatPct(((stockCounts[s] ?? 0) / stockTotal) * 100, false, 0)
											: '—'}</span
									>
								</a>
							</li>
						{/each}
					</ul>
				</div>
			</section>

			<!-- Recomendaciones y presupuesto -->
			<section class="halves reveal" style:--i="8">
				<div class="card">
					<div class="card-head">
						<div>
							<span class="eyebrow">Inteligencia artificial</span>
							<h2>Recomendaciones de hoy</h2>
						</div>
						<a class="more" href="{resolve('/inventario')}?tab=recomendaciones">Ver {recs.length}</a
						>
					</div>
					{#if urgent.length > 0}
						<ul class="rows">
							{#each urgent.slice(0, 5) as rec (rec.product_id + '-' + rec.warehouse_id)}
								{@const pr = RECOMMENDATION_PRIORITY[rec.priority]}
								{@const kind = RECOMMENDATION_KIND[rec.kind]}
								<li>
									<span class="pri {pr?.tone ?? 'neutral'}" title={pr?.label}></span>
									<div class="row-main">
										<span class="row-title">{rec.title}</span>
										<span class="row-sub"
											><span class="mono">{rec.sku}</span> · {rec.product_name} · {rec.warehouse_code}</span
										>
									</div>
									<Badge tone={kind?.tone ?? 'neutral'}>{kind?.label ?? rec.kind}</Badge>
								</li>
							{/each}
						</ul>
						{#if urgent.length > 5}
							<p class="foot">Y {urgent.length - 5} más en Inventario.</p>
						{/if}
					{:else}
						<p class="empty">
							Nada urgente: ningún producto está en quiebre ni por debajo de su punto de reorden sin
							una compra en camino.
						</p>
					{/if}
				</div>

				<div class="card">
					<div class="card-head">
						<div>
							<span class="eyebrow">Finanzas</span>
							<h2>Presupuesto de compras del mes</h2>
						</div>
					</div>
					<ul class="budgets">
						{#each budgets as b, i (b.id)}
							<li>
								<div class="b-head">
									<span>{b.category_name}</span>
									<span class="spacer"></span>
									<span class="b-amount tabular"
										>{formatCopShort(Number(b.committed) + Number(b.spent))}
										<span class="text-muted">de {formatCopShort(b.amount)}</span></span
									>
									<strong class="tabular">{formatPct(b.usage_pct)}</strong>
								</div>
								<div class="track">
									<span
										class="fill"
										style:width="{Math.min(100, b.usage_pct)}%"
										style:background={budgetTone(b.usage_pct)}
										style:--d="{300 + i * 80}ms"
									></span>
									<span class="limit" style:left="85%"></span>
								</div>
							</li>
						{/each}
					</ul>
					<p class="foot">
						La marca indica el 85 %: al pasarlo, la IA prioriza las compras de la categoría y las
						restantes van a aprobación.
					</p>
				</div>
			</section>

			<!-- Actividad -->
			<section class="halves reveal" style:--i="9">
				<div class="card">
					<div class="card-head">
						<div>
							<span class="eyebrow">Bitácora</span>
							<h2>Últimas decisiones de la IA</h2>
						</div>
						<a class="more" href={resolve('/ia/decisiones')}>Ver bitácora</a>
					</div>
					<ol class="timeline">
						{#each decisions as d (d.id)}
							{@const st = DECISION_STATUS[d.status] ?? {
								label: d.status,
								tone: 'neutral' as const
							}}
							<li>
								<span class="node {st.tone}"></span>
								<div class="row-main">
									<span class="row-title">{d.title ?? DECISION_TYPE[d.decision_type]}</span>
									<span class="row-sub">
										{DECISION_TYPE[d.decision_type] ?? d.decision_type} · {formatDateTime(
											d.sim_time
										)}
										{#if d.amount}· {formatCop(d.amount)}{/if}
									</span>
								</div>
								<Badge tone={st.tone}>{st.label}</Badge>
							</li>
						{:else}
							<li class="empty">Todavía no hay decisiones. Inicia la simulación.</li>
						{/each}
					</ol>
				</div>

				<div class="col">
					<div class="card" id="alertas">
						<div class="card-head">
							<div>
								<span class="eyebrow">Alertas</span>
								<h2>Para revisar</h2>
							</div>
						</div>
						{#if alertGroups.length > 0}
							<ul class="rows">
								{#each alertGroups as g (g.type)}
									<li>
										<span class="pri {g.tone}"></span>
										<div class="row-main">
											<span class="row-title">{g.label}</span>
											<span class="row-sub clamp">{g.latest.message}</span>
										</div>
										<span class="pill tabular">{g.count}</span>
									</li>
								{/each}
							</ul>
						{:else}
							<p class="empty">Sin alertas abiertas.</p>
						{/if}
					</div>

					<div class="card">
						<div class="card-head">
							<div>
								<span class="eyebrow">En vivo</span>
								<h2>Ventas recientes</h2>
							</div>
							{#if clock.status === 'running'}<span class="pulse" aria-label="En marcha"
								></span>{/if}
						</div>
						<ul class="feed">
							{#each feed as s (s.id)}
								<li
									in:fly={{ y: -12, duration: 450, easing: cubicOut }}
									animate:flip={{ duration: 350, easing: cubicOut }}
								>
									<span class="mono time">{formatTime(s.ordered_at)}</span>
									<div class="row-main">
										<span class="row-title">{s.customer_name}</span>
										<span class="row-sub mono">{s.number}</span>
									</div>
									<Badge tone={SALES_STATUS[s.status]?.tone ?? 'neutral'}
										>{SALES_STATUS[s.status]?.label ?? s.status}</Badge
									>
									<strong class="tabular amount">{formatCop(s.total)}</strong>
								</li>
							{:else}
								<li class="empty">
									Los pedidos aparecen aquí cuando la simulación está en marcha.
								</li>
							{/each}
						</ul>
					</div>
				</div>
			</section>
		</div>
	{/if}
</LoadState>

<style>
	/* ---------- Encabezado ---------- */
	.hero {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: var(--space-4);
		flex-wrap: wrap;
		margin-bottom: var(--space-5);
	}

	.hero h1 {
		margin: var(--space-1) 0 0;
		font-size: var(--text-3xl);
		letter-spacing: var(--tracking-tight);
	}

	.hero p {
		margin: var(--space-1) 0 0;
		color: var(--color-text-muted);
	}

	.cta {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3) var(--space-2) var(--space-4);
		border-radius: var(--radius-full);
		background: var(--color-primary);
		color: var(--color-on-primary);
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
		text-decoration: none;
		box-shadow: var(--shadow-sm);
		transition:
			background var(--duration-base) var(--ease-out),
			transform var(--duration-base) var(--ease-out),
			box-shadow var(--duration-base) var(--ease-out);
	}

	.cta:hover {
		background: var(--color-primary-hover);
		transform: translateY(-1px);
		box-shadow: var(--shadow-md);
	}

	.count {
		min-width: 1.5rem;
		padding: 0 var(--space-2);
		border-radius: var(--radius-full);
		background: color-mix(in srgb, var(--color-on-primary) 22%, transparent);
		text-align: center;
	}

	/* ---------- Estructura ---------- */
	.board {
		display: grid;
		gap: var(--space-5);
	}

	.card {
		border-color: var(--color-border-subtle);
	}

	.card-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--space-3);
		margin-bottom: var(--space-4);
	}

	.card-head h2 {
		margin: 0.125rem 0 0;
		font-size: var(--text-lg);
		letter-spacing: var(--tracking-tight);
	}

	.more {
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
		text-decoration: none;
		white-space: nowrap;
	}

	.more:hover {
		text-decoration: underline;
	}

	.metrics {
		display: grid;
		gap: var(--space-4);
		grid-template-columns: repeat(4, minmax(0, 1fr));
	}

	.metrics > div {
		display: grid;
	}

	.split {
		display: grid;
		gap: var(--space-4);
		grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
	}

	.halves {
		display: grid;
		gap: var(--space-4);
		grid-template-columns: repeat(2, minmax(0, 1fr));
		align-items: start;
	}

	.col {
		display: grid;
		gap: var(--space-4);
	}

	.spacer {
		flex: 1;
	}

	/* ---------- Requiere atención ---------- */
	.attention {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		flex-wrap: wrap;
	}

	.attention ul {
		display: flex;
		gap: var(--space-2);
		flex-wrap: wrap;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.chip {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-full);
		border: 1px solid transparent;
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
		text-decoration: none;
		transition:
			transform var(--duration-base) var(--ease-out),
			box-shadow var(--duration-base) var(--ease-out);
	}

	.chip:hover {
		transform: translateY(-1px);
		box-shadow: var(--shadow-sm);
	}

	.chip :global(.go) {
		transition: transform var(--duration-base) var(--ease-out);
	}

	.chip:hover :global(.go) {
		transform: translateX(3px);
	}

	.chip.warning {
		background: var(--color-warning-bg);
		color: var(--color-warning);
		border-color: color-mix(in srgb, var(--color-warning) 25%, transparent);
	}

	.chip.danger {
		background: var(--color-danger-bg);
		color: var(--color-danger);
		border-color: color-mix(in srgb, var(--color-danger) 25%, transparent);
	}

	.ok {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		margin: 0;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-full);
		background: var(--color-success-bg);
		color: var(--color-success);
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
	}

	/* ---------- Franja secundaria ---------- */
	.strip {
		display: grid;
		grid-template-columns: repeat(5, minmax(0, 1fr));
		padding: var(--space-4) 0;
	}

	.strip > div {
		display: grid;
		gap: 0.125rem;
		padding: 0 var(--space-5);
		border-left: 1px solid var(--color-border-subtle);
	}

	.strip > div:first-child {
		border-left: 0;
	}

	.strip strong {
		font-size: var(--text-xl);
		letter-spacing: var(--tracking-tight);
	}

	.sub {
		font-size: var(--text-xs);
		color: var(--color-text-muted);
	}

	.sub.warn {
		color: var(--color-warning);
		font-weight: var(--weight-medium);
	}

	/* ---------- Inventario ---------- */
	.segments {
		display: flex;
		gap: 3px;
		height: 12px;
		margin-bottom: var(--space-4);
	}

	.segments span {
		flex-basis: 0;
		min-width: 6px;
		border-radius: var(--radius-full);
		transform-origin: left;
		animation: grow 1s var(--ease-out) both;
		animation-delay: calc(300ms + var(--d));
	}

	.legend {
		display: grid;
		gap: 0.125rem;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.legend a {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2);
		margin: 0 calc(var(--space-2) * -1);
		border-radius: var(--radius-md);
		color: var(--color-text);
		text-decoration: none;
		font-size: var(--text-sm);
		transition: background var(--duration-fast) var(--ease-out);
	}

	.legend a:hover {
		background: var(--color-surface-muted);
	}

	.swatch {
		width: 0.625rem;
		height: 0.625rem;
		border-radius: 3px;
	}

	.pct {
		width: 3rem;
		text-align: right;
		color: var(--color-text-muted);
	}

	/* ---------- Listas ---------- */
	.rows,
	.timeline,
	.feed {
		display: grid;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.rows li,
	.feed li {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) 0;
		border-top: 1px solid var(--color-border-subtle);
	}

	.rows li:first-child,
	.feed li:first-child {
		border-top: 0;
		padding-top: 0;
	}

	.row-main {
		display: grid;
		flex: 1;
		min-width: 0;
	}

	.row-title {
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.row-sub {
		font-size: var(--text-xs);
		color: var(--color-text-muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.row-sub.clamp {
		white-space: normal;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
	}

	.pri {
		flex: none;
		width: 0.5rem;
		height: 0.5rem;
		border-radius: var(--radius-full);
		background: var(--color-text-muted);
	}

	.pri.danger {
		background: var(--color-danger);
		box-shadow: 0 0 0 4px var(--color-danger-bg);
	}
	.pri.warning {
		background: var(--color-warning);
		box-shadow: 0 0 0 4px var(--color-warning-bg);
	}
	.pri.info {
		background: var(--color-info);
		box-shadow: 0 0 0 4px var(--color-info-bg);
	}

	.pill {
		min-width: 1.75rem;
		padding: 0.125rem var(--space-2);
		border-radius: var(--radius-full);
		background: var(--color-surface-muted);
		font-size: var(--text-xs);
		font-weight: var(--weight-bold);
		text-align: center;
	}

	/* Línea de tiempo de decisiones */
	.timeline li {
		position: relative;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) 0 var(--space-3) var(--space-5);
	}

	.timeline li::before {
		content: '';
		position: absolute;
		left: 0.3rem;
		top: 0;
		bottom: 0;
		width: 1px;
		background: var(--color-border);
	}

	.timeline li:first-child::before {
		top: 50%;
	}

	.timeline li:last-child::before {
		bottom: 50%;
	}

	.timeline li:only-child::before {
		display: none;
	}

	.node {
		position: absolute;
		left: 0;
		top: 50%;
		width: 0.65rem;
		height: 0.65rem;
		margin-top: -0.325rem;
		border-radius: var(--radius-full);
		background: var(--color-surface);
		border: 2px solid var(--color-text-muted);
	}

	.node.success {
		border-color: var(--color-success);
	}
	.node.warning {
		border-color: var(--color-warning);
		background: var(--color-warning);
	}
	.node.danger {
		border-color: var(--color-danger);
	}
	.node.info {
		border-color: var(--color-info);
	}

	/* Presupuesto */
	.budgets {
		display: grid;
		gap: var(--space-4);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.b-head {
		display: flex;
		align-items: baseline;
		gap: var(--space-3);
		margin-bottom: var(--space-2);
		font-size: var(--text-sm);
	}

	.b-amount {
		font-size: var(--text-xs);
	}

	.b-head strong {
		width: 3.5rem;
		text-align: right;
	}

	.track {
		position: relative;
		height: 8px;
		border-radius: var(--radius-full);
		background: var(--color-track);
	}

	.track .fill {
		position: absolute;
		inset: 0 auto 0 0;
		min-width: 4px;
		border-radius: inherit;
		transform-origin: left;
		animation: grow 1.1s var(--ease-out) both;
		animation-delay: var(--d);
		transition:
			width var(--duration-slow) var(--ease-out),
			background var(--duration-slow) var(--ease-out);
	}

	.limit {
		position: absolute;
		top: -3px;
		bottom: -3px;
		width: 2px;
		border-radius: 1px;
		background: var(--color-text-muted);
		opacity: 0.45;
	}

	/* Ventas en vivo */
	.time {
		color: var(--color-text-muted);
		font-size: var(--text-xs);
	}

	.amount {
		font-size: var(--text-sm);
		min-width: 6.5rem;
		text-align: right;
	}

	.pulse {
		position: relative;
		width: 0.6rem;
		height: 0.6rem;
		margin-top: var(--space-2);
		border-radius: var(--radius-full);
		background: var(--color-success);
	}

	.pulse::after {
		content: '';
		position: absolute;
		inset: 0;
		border-radius: inherit;
		background: var(--color-success);
		animation: ping 1.8s var(--ease-out) infinite;
	}

	.foot,
	.empty {
		margin: var(--space-3) 0 0;
		font-size: var(--text-sm);
		color: var(--color-text-muted);
	}

	li.empty {
		display: block;
		border: 0;
		padding: 0;
		margin: 0;
	}

	.mono {
		font-family: var(--font-mono);
	}

	@keyframes grow {
		from {
			transform: scaleX(0);
		}
	}

	@keyframes ping {
		75%,
		100% {
			transform: scale(2.4);
			opacity: 0;
		}
	}

	/* ---------- Responsivo ---------- */
	@media (max-width: 1200px) {
		.metrics {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}

		.strip {
			grid-template-columns: repeat(3, minmax(0, 1fr));
			row-gap: var(--space-4);
		}

		.strip > div:nth-child(4) {
			border-left: 0;
		}
	}

	@media (max-width: 960px) {
		.split,
		.halves {
			grid-template-columns: minmax(0, 1fr);
		}
	}

	@media (max-width: 600px) {
		.metrics {
			grid-template-columns: minmax(0, 1fr);
		}

		.strip {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}

		.strip > div {
			border-left: 0;
		}

		.amount {
			min-width: 0;
		}
	}
</style>
