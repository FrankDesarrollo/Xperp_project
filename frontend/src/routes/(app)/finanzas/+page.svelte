<script lang="ts">
	import { onMount } from 'svelte';
	import { resolve } from '$app/paths';
	import { Bot, ScanSearch, Sparkles } from '@lucide/svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { BudgetProjection, FinanceOverview, FinancialReview } from '$lib/api/types';
	import { toasts } from '$lib/stores/toasts.svelte';
	import KpiTile from '$lib/components/domain/KpiTile.svelte';
	import StatusBadge from '$lib/components/domain/StatusBadge.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import EmptyState from '$lib/components/ui/EmptyState.svelte';
	import {
		formatCop,
		formatCopShort,
		formatDate,
		formatDateTime,
		formatPct
	} from '$lib/utils/format';
	import { FINDING_SEVERITY, TRIGGER_KIND, type Tone } from '$lib/utils/labels';

	let overview = $state<FinanceOverview | null>(null);
	let reviews = $state<FinancialReview[]>([]);
	let analyzing = $state(false);

	const loader = new Loader();

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function load() {
		return loader.run(async () => {
			const [o, r] = await Promise.all([
				unwrap(api.GET('/dashboard/finance')),
				unwrap(api.GET('/ai/financial-reviews', { params: { query: { size: 6 } } }))
			]);
			overview = o;
			reviews = r.items;
		});
	}

	/** Pide un análisis ahora. Las reglas calculan; el LLM, si está, solo redacta el resumen. */
	async function analyze() {
		analyzing = true;
		try {
			const review = await unwrap(api.POST('/ai/financial-reviews'));
			reviews = [review, ...reviews].slice(0, 6);
			toasts.success(
				'Análisis listo',
				review.findings.length === 0
					? 'Sin hallazgos'
					: `${review.findings.length} hallazgos para revisar`
			);
		} catch (e) {
			toasts.error('No se pudo analizar', e instanceof ApiError ? e.message : undefined);
		} finally {
			analyzing = false;
		}
	}

	function budgetTone(b: BudgetProjection): Tone {
		if (b.projected_pct > 100 || b.used_pct >= 100) return 'danger';
		if (b.used_pct >= 85) return 'warning';
		return 'success';
	}

	function budgetLabel(b: BudgetProjection): string {
		if (b.projected_pct > 100 || b.used_pct >= 100) return 'Se pasa';
		if (b.used_pct >= 85) return 'Cerca del tope';
		return 'En rango';
	}

	let latest = $derived(reviews[0] ?? null);
	let older = $derived(reviews.slice(1));

	onMount(() => {
		void load();
	});
</script>

<svelte:head><title>Finanzas · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Finanzas</h1>
		<p>
			Ingresos, gastos, presupuesto y capital del mes, calculados por el sistema. La IA los revisa
			los lunes, al cierre del mes, cuando aparece una condición grave o cuando lo pides. Lo que
			recomienda lo decide una persona.
		</p>
	</div>
	<Button variant="primary" loading={analyzing} onclick={analyze}
		><ScanSearch size={16} />Analizar ahora</Button
	>
</div>

<LoadState {loader} what="las finanzas" onretry={load}>
	{#if overview}
		<div class="stack">
			<p class="small text-muted">
				Del {formatDate(overview.from)} al {formatDate(overview.to)} · día {overview.days_elapsed} de
				{overview.days_in_period}
			</p>
			<section class="grid-kpi" aria-label="Cifras del mes">
				<KpiTile
					label="Ventas"
					value={formatCopShort(overview.revenue)}
					hint="Costo {formatCopShort(overview.cost_of_sales)}"
					tone="info"
				/>
				<KpiTile
					label="Margen bruto"
					value={formatCopShort(overview.gross_margin)}
					hint={formatPct(overview.gross_margin_pct)}
					tone={overview.gross_margin_pct < 18 ? 'warning' : 'success'}
				/>
				<KpiTile
					label="Fletes"
					value={formatCopShort(overview.freight)}
					hint="{formatPct(overview.freight_pct)} de las ventas"
					tone={overview.freight_pct >= 8 ? 'warning' : 'neutral'}
				/>
				<KpiTile
					label="Margen después de fletes"
					value={formatCopShort(overview.margin_after_freight)}
				/>
				<KpiTile
					label="Compras recibidas"
					value={formatCopShort(overview.purchases_received)}
					hint="{formatCopShort(overview.purchases_committed)} comprometidas en camino"
				/>
				<KpiTile
					label="Ventas perdidas"
					value={formatCopShort(overview.lost_sales_amount)}
					hint="Por falta de stock"
					tone={Number(overview.lost_sales_amount) > 0 ? 'warning' : 'success'}
				/>
				<KpiTile
					label="Cuentas por pagar"
					value={formatCopShort(overview.payables_open)}
					hint="{formatCopShort(overview.payables_due_7d)} vencen en 7 días · {formatCopShort(
						overview.payables_overdue
					)} vencidas"
					tone={Number(overview.payables_overdue) > 0 ? 'danger' : 'neutral'}
				/>
				<KpiTile
					label="Inventario a costo"
					value={formatCopShort(overview.inventory_value)}
					hint="{formatCopShort(overview.inventory_excess_value)} en exceso · {formatCopShort(
						overview.slow_mover_value
					)} estancado"
				/>
			</section>

			<section class="card" aria-labelledby="review-title">
				<div class="row">
					<h2 id="review-title">Último análisis de la IA</h2>
				</div>
				{#if latest}
					<article class="review">
						<div class="row tags">
							<Badge tone="ai"><Bot size={12} />Revisión financiera</Badge>
							<StatusBadge map={TRIGGER_KIND} value={latest.trigger_kind} />
							{#if latest.llm_used}<Badge tone="ai"><Sparkles size={12} />LLM</Badge>{/if}
							<span class="spacer"></span>
							<span class="small text-muted">{formatDateTime(latest.sim_time)}</span>
						</div>
						<p class="summary">{latest.summary}</p>
						{#if latest.findings.length === 0}
							<p class="small text-muted">
								Sin hallazgos: presupuesto, pagos, inventario, fletes y margen en rango.
							</p>
						{:else}
							<ul class="findings">
								{#each latest.findings as f (f.code + f.title)}
									<li>
										<div class="row">
											<StatusBadge map={FINDING_SEVERITY} value={f.severity} />
											<strong>{f.title}</strong>
											{#if f.impact}<span class="small numeric">{formatCop(f.impact)}</span>{/if}
										</div>
										<p class="small text-muted">{f.detail}</p>
										<p class="small">
											<strong>Recomendación:</strong>
											{f.recommendation}
											{#if f.link}
												<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- resolve() abajo -->
												<a href={resolve(f.link as '/tablero')}>Ir a revisar</a>
											{/if}
										</p>
									</li>
								{/each}
							</ul>
						{/if}
					</article>
				{:else}
					<EmptyState title="Todavía no hay análisis"
						>Pulsa «Analizar ahora» o espera la revisión del lunes.</EmptyState
					>
				{/if}
				{#if older.length > 0}
					<details class="older">
						<summary class="small">Análisis anteriores ({older.length})</summary>
						<ul class="small">
							{#each older as r (r.id)}
								<li>
									{formatDateTime(r.sim_time)} · {TRIGGER_KIND[r.trigger_kind]?.label ??
										r.trigger_kind} · {r.findings.length} hallazgos
								</li>
							{/each}
						</ul>
					</details>
				{/if}
			</section>

			<section class="card" aria-labelledby="budget-title">
				<h2 id="budget-title">Presupuesto de compras del mes</h2>
				{#if overview.budgets.length === 0}
					<p class="small text-muted">No hay presupuesto cargado para este mes.</p>
				{:else}
					<div class="table-wrap">
						<table class="data">
							<thead
								><tr
									><th>Categoría</th><th class="numeric">Presupuesto</th><th class="numeric"
										>Usado</th
									><th class="numeric">Proyección al cierre</th><th class="numeric">Faltaría</th><th
										>Estado</th
									></tr
								></thead
							>
							<tbody>
								{#each overview.budgets as b (b.category_id)}
									<tr>
										<td>{b.category_name}</td>
										<td class="numeric">{formatCop(b.amount)}</td>
										<td class="numeric">{formatPct(b.used_pct)}</td>
										<td class="numeric">{formatPct(b.projected_pct)}</td>
										<td class="numeric"
											>{Number(b.projected_overrun) > 0 ? formatCop(b.projected_overrun) : '—'}</td
										>
										<td><Badge tone={budgetTone(b)}>{budgetLabel(b)}</Badge></td>
									</tr>
								{/each}
							</tbody>
						</table>
					</div>
					<p class="small text-muted">
						La proyección suma a lo usado lo que se repondría al ritmo de la demanda, a costo, en
						los días que faltan.
					</p>
				{/if}
			</section>
		</div>
	{/if}
</LoadState>

<style>
	.review {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
		border-left: 3px solid var(--color-ai);
		border-radius: var(--radius-lg);
		background: var(--color-surface-muted);
	}

	.tags {
		flex-wrap: wrap;
	}

	.summary {
		margin: 0;
		line-height: 1.55;
	}

	.findings {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-3);
	}

	.findings li {
		display: grid;
		gap: var(--space-1);
		padding-top: var(--space-3);
		border-top: 1px solid var(--color-border);
	}

	.findings .row {
		flex-wrap: wrap;
	}

	.findings p {
		margin: 0;
	}

	@media (pointer: coarse) {
		.findings a {
			display: inline-flex;
			align-items: center;
			min-height: 2.75rem;
		}
	}

	.older {
		margin-top: var(--space-3);
	}

	.older summary {
		cursor: pointer;
		min-height: 2.75rem;
		display: flex;
		align-items: center;
	}

	.older ul {
		margin: 0;
		padding-left: var(--space-5);
	}
</style>
