<script lang="ts">
	import { onMount } from 'svelte';
	import { resolve } from '$app/paths';
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
	import { clock } from '$lib/stores/clock.svelte';
	import KpiTile from '$lib/components/domain/KpiTile.svelte';
	import DecisionCard from '$lib/components/domain/DecisionCard.svelte';
	import RecommendationCard from '$lib/components/domain/RecommendationCard.svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import StockBadge from '$lib/components/domain/StockBadge.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import EChart from '$lib/components/charts/EChart.svelte';
	import { barOption, lineOption } from '$lib/components/charts/options';
	import {
		formatCop,
		formatCopShort,
		formatNumber,
		formatPct,
		formatTime
	} from '$lib/utils/format';
	import { SEVERITY, SALES_STATUS } from '$lib/utils/labels';

	interface LiveSale {
		id: number;
		number: string;
		customer_name: string;
		total: string;
		status: string;
		ordered_at: string;
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
	let urgent = $derived(recs.filter((r) => r.priority !== 'low'));
	const loader = new Loader();

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
			unwrap(api.GET('/ai/alerts', { params: { query: { limit: 8 } } })),
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
		let timer: ReturnType<typeof setTimeout> | null = null;
		const refresh = () => {
			if (timer) return;
			timer = setTimeout(() => {
				timer = null;
				void loader.run(load);
			}, 3000);
		};
		const offs = [
			live.on('ai.decision', refresh),
			live.on('alert.created', refresh),
			live.on('clock.tick', refresh),
			live.on('sale.created', (data) => {
				feed = [data as LiveSale, ...feed].slice(0, 8);
			})
		];
		return () => {
			offs.forEach((o) => o());
			if (timer) clearTimeout(timer);
		};
	});
</script>

<svelte:head><title>Tablero · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Tablero</h1>
		<p>
			Operación de Distribuidora La Ceiba en tiempo real · mes en curso hasta el día {clock.dayNumber}
		</p>
	</div>
</div>

<LoadState {loader} what="los indicadores" onretry={() => loader.run(load)}>
	{#if kpis}
		<div class="stack">
			<section class="grid-kpi" aria-label="Indicadores">
				<KpiTile
					label="Ventas de hoy"
					value={formatCopShort(kpis.sales_today)}
					hint="{kpis.orders_today} pedidos"
					tone="info"
				/>
				<KpiTile
					label="Ventas del mes"
					value={formatCopShort(kpis.sales_period)}
					hint="Margen {formatPct(kpis.gross_margin_pct)}"
					tone="info"
				/>
				<KpiTile
					label="Nivel de servicio"
					value={formatPct(kpis.service_level, true)}
					hint="{formatNumber(kpis.lost_sales_units)} und perdidas · {formatCopShort(
						kpis.lost_sales_amount
					)}"
					tone={kpis.service_level >= 0.97
						? 'success'
						: kpis.service_level >= 0.93
							? 'warning'
							: 'danger'}
				/>
				<KpiTile
					label="SKU en quiebre"
					value={String(kpis.stockout_skus)}
					hint="{kpis.low_stock_skus} en nivel bajo"
					tone={kpis.stockout_skus === 0 ? 'success' : 'danger'}
				/>
				<KpiTile
					label="OC abiertas"
					value={String(kpis.open_purchase_orders)}
					hint="{kpis.late_purchase_orders} atrasadas"
					tone={kpis.late_purchase_orders > 0 ? 'warning' : 'success'}
				/>
				<KpiTile
					label="Presupuesto ejecutado"
					value={formatPct(kpis.budget_execution_pct)}
					hint="Compras {formatCopShort(kpis.purchases_period)}"
					tone={kpis.budget_execution_pct > 85 ? 'warning' : 'info'}
				/>
				<KpiTile
					label="Decisiones de la IA"
					value={formatNumber(kpis.decisions.executed + kpis.decisions.approved)}
					hint="{kpis.decisions.pending_approval} por aprobar · {kpis.decisions.blocked} bloqueadas"
					tone={kpis.decisions.pending_approval > 0 ? 'warning' : 'success'}
				/>
				<KpiTile
					label="Sobrecosto asumido"
					value={formatCopShort(kpis.overcost_assumed)}
					hint={kpis.avg_approval_minutes
						? `Aprobación promedio ${formatNumber(kpis.avg_approval_minutes / 60, 1)} h`
						: 'Reasignaciones de OC'}
				/>
			</section>

			<section class="card" aria-labelledby="recs-title">
				<div class="row">
					<h2 id="recs-title">Qué recomienda la IA hoy</h2>
					<span class="spacer"></span>
					<a class="small" href="{resolve('/inventario')}?tab=recomendaciones"
						>Ver las {recs.length} recomendaciones</a
					>
				</div>
				{#if urgent.length > 0}
					<div class="recs">
						{#each urgent.slice(0, 4) as rec (rec.product_id + '-' + rec.warehouse_id)}
							<RecommendationCard {rec} compact />
						{/each}
					</div>
					{#if urgent.length > 4}
						<p class="text-muted small more">
							…y {urgent.length - 4} más por reponer en Inventario.
						</p>
					{/if}
				{:else}
					<p class="text-muted small">
						Nada urgente: ningún producto está en quiebre ni por debajo de su punto de reorden sin
						una compra en camino.
					</p>
				{/if}
			</section>

			<section class="grid-2">
				<div class="card">
					<h2>Ventas diarias · últimos 30 días</h2>
					{#if sales}
						<EChart
							label="Ventas diarias"
							option={(t) =>
								lineOption(t, [{ name: 'Ventas', points: sales!.points, area: true }], 'money')}
						/>
					{/if}
				</div>
				<div class="card">
					<h2>Presupuesto de compras del mes</h2>
					<EChart
						label="Uso del presupuesto por categoría"
						height="220px"
						option={(t) =>
							barOption(
								t,
								budgets.map((b) => ({
									label: b.category_name,
									value: b.usage_pct,
									tone: b.usage_pct > 85 ? 'danger' : b.usage_pct > 70 ? 'warning' : undefined
								})),
								(v) => formatPct(v),
								Math.max(100, ...budgets.map((b) => b.usage_pct))
							)}
					/>
					<p class="text-muted small">
						Las barras en rojo pasan el 85 % y activan la priorización de la IA.
					</p>
				</div>
			</section>

			<section class="grid-2">
				<div class="card">
					<div class="row">
						<h2>Últimas decisiones de la IA</h2>
						<span class="spacer"></span>
						<a class="small" href={resolve('/ia/decisiones')}>Ver bitácora</a>
					</div>
					<div class="stack">
						{#each decisions as d (d.id)}
							<DecisionCard decision={d} compact />
						{:else}
							<p class="text-muted small">Todavía no hay decisiones. Inicia la simulación.</p>
						{/each}
					</div>
				</div>
				<div class="stack">
					<div class="card">
						<h2>Inventario por estado</h2>
						<div class="states">
							{#each ['stockout', 'low', 'ok', 'over'] as s (s)}
								<a href="{resolve('/inventario')}?status={s}" class="state">
									<StockBadge status={s} />
									<strong>{stockCounts[s] ?? 0}</strong>
								</a>
							{/each}
						</div>
					</div>
					<div class="card">
						<h2>Nivel de servicio diario</h2>
						{#if service}
							<EChart
								label="Nivel de servicio diario"
								height="180px"
								option={(t) =>
									lineOption(t, [{ name: 'Nivel de servicio', points: service!.points }], 'ratio')}
							/>
						{/if}
					</div>
					<div class="card">
						<h2>Alertas</h2>
						<ul class="list">
							{#each alerts as a (a.id)}
								<li>
									<Badge tone={SEVERITY[a.severity] ?? 'info'}
										>{a.severity === 'critical'
											? 'Crítica'
											: a.severity === 'warning'
												? 'Atención'
												: 'Info'}</Badge
									>
									<span class="small">{a.message}</span>
								</li>
							{:else}
								<li class="text-muted small">Sin alertas.</li>
							{/each}
						</ul>
					</div>
					<div class="card">
						<h2>Ventas en vivo</h2>
						<ul class="list">
							{#each feed as s (s.id)}
								<li>
									<span class="mono small">{formatTime(s.ordered_at)}</span>
									<span class="small truncate">{s.number} · {s.customer_name}</span>
									<span class="spacer"></span>
									<Badge tone={SALES_STATUS[s.status]?.tone ?? 'neutral'}
										>{SALES_STATUS[s.status]?.label ?? s.status}</Badge
									>
									<strong class="small">{formatCop(s.total)}</strong>
								</li>
							{:else}
								<li class="text-muted small">
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
	.states {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: var(--space-3);
	}

	.state {
		display: grid;
		justify-items: start;
		gap: var(--space-2);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		text-decoration: none;
		color: var(--color-text);
	}

	.state strong {
		font-size: var(--text-xl);
	}

	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-2);
	}

	.list li {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	h2 {
		margin: 0 0 var(--space-3);
	}

	.recs {
		display: grid;
		gap: var(--space-3);
		grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
	}

	.more {
		margin: var(--space-3) 0 0;
	}

	@media (max-width: 600px) {
		.states {
			grid-template-columns: repeat(2, 1fr);
		}
	}
</style>
