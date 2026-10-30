<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type {
		Category,
		ForecastSeries,
		InventoryCount,
		InventoryItem,
		InventoryMovement,
		StockTransfer,
		SupplierOffer,
		Warehouse
	} from '$lib/api/types';
	import { auth } from '$lib/stores/auth.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import StockBadge from '$lib/components/domain/StockBadge.svelte';
	import StatusBadge from '$lib/components/domain/StatusBadge.svelte';
	import Drawer from '$lib/components/ui/Drawer.svelte';
	import Modal from '$lib/components/ui/Modal.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Tabs from '$lib/components/ui/Tabs.svelte';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import EChart from '$lib/components/charts/EChart.svelte';
	import { lineOption } from '$lib/components/charts/options';
	import { formatCop, formatDateTime, formatNumber, formatPct } from '$lib/utils/format';
	import { TRANSFER_STATUS } from '$lib/utils/labels';

	const MOVEMENT: Record<string, string> = {
		sale: 'Venta',
		purchase_receipt: 'Recepción de compra',
		adjustment: 'Ajuste',
		transfer_out: 'Traslado (salida)',
		transfer_in: 'Traslado (entrada)',
		return: 'Devolución'
	};

	let tab = $state('stock');
	let warehouses = $state<Warehouse[]>([]);
	let categories = $state<Category[]>([]);
	let items = $state<InventoryItem[]>([]);
	let total = $state(0);
	let pageNo = $state(1);
	let filters = $state({
		warehouse_id: '',
		status: page.url.searchParams.get('status') ?? '',
		category_code: '',
		q: ''
	});
	let transfers = $state<StockTransfer[]>([]);
	let counts = $state<InventoryCount[]>([]);

	let selected = $state<InventoryItem | null>(null);
	let movements = $state<InventoryMovement[]>([]);
	let movementsTotal = $state(0);
	let movementsPage = $state(1);
	let forecast = $state<ForecastSeries | null>(null);
	let offers = $state<SupplierOffer[]>([]);

	let adjustOpen = $state(false);
	let countOpen = $state(false);
	let adjust = $state({ quantity: 0, reason: '' });
	let countQty = $state(0);
	let busy = $state(false);

	const SIZE = 25;

	async function load() {
		const res = await unwrap(
			api.GET('/inventory', {
				params: {
					query: {
						warehouse_id: filters.warehouse_id ? Number(filters.warehouse_id) : undefined,
						status: filters.status || undefined,
						category_code: filters.category_code || undefined,
						q: filters.q || undefined,
						page: pageNo,
						size: SIZE
					}
				}
			})
		);
		items = res.items;
		total = res.total;
	}

	async function loadTransfers() {
		transfers = (await unwrap(api.GET('/stock-transfers', { params: { query: { size: 50 } } })))
			.items;
	}

	async function loadCounts() {
		counts = await unwrap(api.GET('/inventory/counts'));
	}

	async function open(item: InventoryItem) {
		selected = item;
		movementsPage = 1;
		forecast = null;
		const [f, o] = await Promise.all([
			unwrap(
				api.GET('/ai/forecasts', {
					params: {
						query: {
							product_id: item.product_id,
							warehouse_id: item.warehouse_id,
							history_days: 45
						}
					}
				})
			),
			unwrap(api.GET('/supplier-offers', { params: { query: { product_id: item.product_id } } }))
		]);
		forecast = f;
		offers = o;
		await loadMovements();
	}

	async function loadMovements() {
		if (!selected) return;
		const res = await unwrap(
			api.GET('/inventory/{product_id}/{warehouse_id}/movements', {
				params: {
					path: { product_id: selected.product_id, warehouse_id: selected.warehouse_id },
					query: { page: movementsPage, size: 15 }
				}
			})
		);
		movements = res.items;
		movementsTotal = res.total;
	}

	async function refreshSelected() {
		if (!selected) return;
		selected = await unwrap(
			api.GET('/inventory/{product_id}/{warehouse_id}', {
				params: { path: { product_id: selected.product_id, warehouse_id: selected.warehouse_id } }
			})
		);
		await loadMovements();
	}

	async function submitAdjust() {
		if (!selected) return;
		busy = true;
		try {
			await unwrap(
				api.POST('/inventory/adjustments', {
					body: {
						product_id: selected.product_id,
						warehouse_id: selected.warehouse_id,
						quantity: Number(adjust.quantity),
						reason: adjust.reason
					}
				})
			);
			toasts.success('Ajuste registrado');
			adjustOpen = false;
			await refreshSelected();
			await load();
		} catch (e) {
			toasts.error('No se pudo ajustar', e instanceof ApiError ? e.message : undefined);
		} finally {
			busy = false;
		}
	}

	async function submitCount() {
		if (!selected) return;
		busy = true;
		try {
			const c = await unwrap(
				api.POST('/inventory/counts', {
					body: {
						product_id: selected.product_id,
						warehouse_id: selected.warehouse_id,
						counted_qty: Number(countQty)
					}
				})
			);
			toasts.success(
				'Conteo registrado',
				c.difference === 0
					? 'Coincide con el kardex'
					: `Diferencia de ${c.difference} und; la IA lo revisará`
			);
			countOpen = false;
			await loadCounts();
		} catch (e) {
			toasts.error('No se pudo registrar el conteo', e instanceof ApiError ? e.message : undefined);
		} finally {
			busy = false;
		}
	}

	onMount(() => {
		void (async () => {
			[warehouses, categories] = await Promise.all([
				unwrap(api.GET('/warehouses')),
				unwrap(api.GET('/categories'))
			]);
			await load();
			const productParam = Number(page.url.searchParams.get('product'));
			if (productParam) {
				const res = await unwrap(api.GET('/inventory', { params: { query: { size: 200 } } }));
				const found = res.items.find((i) => i.product_id === productParam);
				if (found) void open(found);
			}
			if (page.url.searchParams.get('id')) tab = 'transfers';
		})();
		let timer: ReturnType<typeof setTimeout> | null = null;
		const off = live.on('inventory.changed', () => {
			if (timer) return;
			timer = setTimeout(() => {
				timer = null;
				void load();
			}, 4000);
		});
		return () => {
			off();
			if (timer) clearTimeout(timer);
		};
	});

	$effect(() => {
		if (tab === 'transfers') void loadTransfers();
		if (tab === 'counts') void loadCounts();
	});

	function applyFilters() {
		pageNo = 1;
		void load();
	}

	let canAdjust = $derived(auth.hasRole('admin', 'manager'));
	let canCount = $derived(auth.hasRole('admin', 'manager', 'warehouse'));
</script>

<svelte:head><title>Inventario · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Inventario</h1>
		<p>Existencias por SKU y bodega con semáforo, kardex y pronóstico de demanda</p>
	</div>
</div>

<Tabs
	tabs={[
		{ id: 'stock', label: 'Existencias', count: total },
		{ id: 'transfers', label: 'Traslados' },
		{ id: 'counts', label: 'Conteos' }
	]}
	active={tab}
	onchange={(id) => (tab = id)}
/>

{#if tab === 'stock'}
	<form class="filters" onsubmit={(e) => (e.preventDefault(), applyFilters())}>
		<Field label="Bodega">
			<select bind:value={filters.warehouse_id} onchange={applyFilters}>
				<option value="">Todas</option>
				{#each warehouses as w (w.id)}<option value={String(w.id)}>{w.code} · {w.name}</option
					>{/each}
			</select>
		</Field>
		<Field label="Estado">
			<select bind:value={filters.status} onchange={applyFilters}>
				<option value="">Todos</option>
				<option value="stockout">Quiebre</option>
				<option value="low">Bajo (incluye quiebre)</option>
				<option value="ok">Normal</option>
				<option value="over">Exceso</option>
			</select>
		</Field>
		<Field label="Categoría">
			<select bind:value={filters.category_code} onchange={applyFilters}>
				<option value="">Todas</option>
				{#each categories as c (c.id)}<option value={c.code}>{c.name}</option>{/each}
			</select>
		</Field>
		<Field label="Buscar">
			<input placeholder="SKU o producto" bind:value={filters.q} />
		</Field>
		<Button type="submit">Filtrar</Button>
	</form>

	<div class="table-wrap">
		<table class="data">
			<thead>
				<tr>
					<th>SKU</th>
					<th>Producto</th>
					<th>Bodega</th>
					<th>Estado</th>
					<th class="num">Disponible</th>
					<th class="num">Reservado</th>
					<th class="num">En tránsito</th>
					<th class="num">Punto de reorden</th>
					<th class="num">Cobertura</th>
					<th class="num">Valor</th>
				</tr>
			</thead>
			<tbody>
				{#each items as i (i.product_id + '-' + i.warehouse_id)}
					<tr
						class="clickable"
						tabindex="0"
						onclick={() => open(i)}
						onkeydown={(e) => e.key === 'Enter' && open(i)}
					>
						<td class="mono">{i.sku}</td>
						<td>
							{i.product_name}
							{#if i.promotion_suggested}<Badge tone="ai">Promoción sugerida</Badge>{/if}
						</td>
						<td>{i.warehouse_code}</td>
						<td><StockBadge status={i.status} /></td>
						<td class="num">{formatNumber(i.available)}</td>
						<td class="num">{formatNumber(i.reserved)}</td>
						<td class="num">{formatNumber(i.in_transit)}</td>
						<td class="num">{formatNumber(i.reorder_point)}</td>
						<td class="num"
							>{i.days_of_cover === null || i.days_of_cover === undefined
								? '—'
								: `${formatNumber(i.days_of_cover, 1)} d`}</td
						>
						<td class="num">{formatCop(i.stock_value)}</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
	<Pagination page={pageNo} size={SIZE} {total} onchange={(p) => ((pageNo = p), load())} />
{:else if tab === 'transfers'}
	<div class="table-wrap">
		<table class="data">
			<thead>
				<tr
					><th>Número</th><th>Ruta</th><th>Productos</th><th>Estado</th><th>Creado</th><th>Salió</th
					><th>Llegó</th></tr
				>
			</thead>
			<tbody>
				{#each transfers as t (t.id)}
					<tr>
						<td class="mono">{t.number}</td>
						<td>{t.from_warehouse_code} → {t.to_warehouse_code}</td>
						<td>{t.items.map((i) => `${i.sku} × ${i.quantity}`).join(', ')}</td>
						<td><StatusBadge map={TRANSFER_STATUS} value={t.status} /></td>
						<td>{formatDateTime(t.created_at)}</td>
						<td>{formatDateTime(t.shipped_at)}</td>
						<td>{formatDateTime(t.received_at)}</td>
					</tr>
				{:else}
					<tr><td colspan="7" class="text-muted">No hay traslados.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
{:else}
	<div class="table-wrap">
		<table class="data">
			<thead>
				<tr
					><th>Fecha</th><th>SKU</th><th>Bodega</th><th class="num">Kardex</th><th class="num"
						>Físico</th
					><th class="num">Diferencia</th><th>Estado</th><th>Contó</th></tr
				>
			</thead>
			<tbody>
				{#each counts as c (c.id)}
					<tr>
						<td>{formatDateTime(c.counted_at)}</td>
						<td class="mono">{c.sku}</td>
						<td>{c.warehouse_code}</td>
						<td class="num">{formatNumber(c.system_qty)}</td>
						<td class="num">{formatNumber(c.counted_qty)}</td>
						<td class="num"
							>{c.difference > 0 ? '+' : ''}{formatNumber(c.difference)} ({formatPct(
								c.difference_pct
							)})</td
						>
						<td>
							<Badge
								tone={c.status === 'discrepancy'
									? 'warning'
									: c.status === 'adjusted'
										? 'success'
										: 'neutral'}
							>
								{c.status === 'discrepancy'
									? 'Con diferencia'
									: c.status === 'adjusted'
										? 'Ajustado'
										: 'Coincide'}
							</Badge>
						</td>
						<td>{c.counted_by ?? '—'}</td>
					</tr>
				{:else}
					<tr><td colspan="8" class="text-muted">No hay conteos registrados.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
{/if}

<Drawer
	open={selected !== null}
	wide
	title={selected ? `${selected.sku} · ${selected.product_name}` : ''}
	subtitle={selected ? `Bodega ${selected.warehouse_code}` : ''}
	onclose={() => (selected = null)}
>
	{#if selected}
		<div class="row">
			<StockBadge status={selected.status} />
			{#if selected.reorder_blocked}<Badge tone="warning">Recompra bloqueada</Badge>{/if}
			{#if selected.promotion_suggested}<Badge tone="ai">Promoción sugerida por la IA</Badge>{/if}
		</div>
		<dl class="facts">
			<dt>En mano</dt>
			<dd>{formatNumber(selected.on_hand)} und</dd>
			<dt>Reservado</dt>
			<dd>{formatNumber(selected.reserved)} und</dd>
			<dt>Disponible</dt>
			<dd><strong>{formatNumber(selected.available)} und</strong></dd>
			<dt>En tránsito</dt>
			<dd>{formatNumber(selected.in_transit)} und</dd>
			<dt>Demanda diaria</dt>
			<dd>{formatNumber(selected.avg_daily_demand, 1)} und</dd>
			<dt>Días de cobertura</dt>
			<dd>
				{selected.days_of_cover === null || selected.days_of_cover === undefined
					? '—'
					: formatNumber(selected.days_of_cover, 1)}
			</dd>
			<dt>Stock de seguridad</dt>
			<dd>{formatNumber(selected.safety_stock)} und</dd>
			<dt>Punto de reorden</dt>
			<dd>{formatNumber(selected.reorder_point)} und</dd>
			<dt>Tope</dt>
			<dd>{formatNumber(selected.max_stock)} und</dd>
			<dt>Cobertura objetivo</dt>
			<dd>{selected.cover_days} días</dd>
			<dt>Valor a costo</dt>
			<dd>{formatCop(selected.stock_value)}</dd>
			<dt>Parámetros</dt>
			<dd>{formatDateTime(selected.params_updated_at)}</dd>
		</dl>
		<div class="row">
			{#if canCount}<Button onclick={() => ((countQty = selected!.on_hand), (countOpen = true))}
					>Registrar conteo</Button
				>{/if}
			{#if canAdjust}<Button
					onclick={() => ((adjust = { quantity: 0, reason: '' }), (adjustOpen = true))}
					>Ajuste manual</Button
				>{/if}
		</div>

		<section class="stack">
			<h3>Demanda y pronóstico</h3>
			{#if forecast}
				<EChart
					label="Demanda histórica y pronóstico"
					option={(t) =>
						lineOption(t, [
							{ name: 'Demanda real', points: forecast!.history },
							{
								name: `Pronóstico (${forecast!.model === 'holt_winters' ? 'Holt-Winters' : 'media móvil'})`,
								points: forecast!.forecast,
								dashed: true
							}
						])}
				/>
			{:else}
				<p class="text-muted small">Cargando pronóstico…</p>
			{/if}
		</section>

		<section class="stack">
			<h3>Ofertas de proveedores</h3>
			<div class="table-wrap">
				<table class="data">
					<thead
						><tr
							><th>Proveedor</th><th class="num">Costo</th><th class="num">Mínimo</th><th
								>Preferido</th
							></tr
						></thead
					>
					<tbody>
						{#each offers as o (o.supplier_id)}
							<tr>
								<td>{o.supplier_code} · {o.supplier_name}</td>
								<td class="num">{formatCop(o.unit_cost)}</td>
								<td class="num">{o.min_order_cases} cajas</td>
								<td>{o.is_preferred ? 'Sí' : ''}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</section>

		<section class="stack">
			<h3>Kardex</h3>
			<div class="table-wrap">
				<table class="data">
					<thead
						><tr
							><th>Fecha</th><th>Movimiento</th><th class="num">Cantidad</th><th class="num"
								>Saldo</th
							><th>Detalle</th></tr
						></thead
					>
					<tbody>
						{#each movements as m (m.id)}
							<tr>
								<td>{formatDateTime(m.occurred_at)}</td>
								<td>{MOVEMENT[m.type] ?? m.type}</td>
								<td class="num" class:neg={m.quantity < 0}
									>{m.quantity > 0 ? '+' : ''}{formatNumber(m.quantity)}</td
								>
								<td class="num">{formatNumber(m.balance_after)}</td>
								<td class="small text-muted"
									>{m.note ?? m.reference_type ?? ''} {m.created_by ? `· ${m.created_by}` : ''}</td
								>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
			<Pagination
				page={movementsPage}
				size={15}
				total={movementsTotal}
				onchange={(p) => ((movementsPage = p), loadMovements())}
			/>
		</section>
	{/if}
</Drawer>

<Modal open={adjustOpen} title="Ajuste manual de inventario" onclose={() => (adjustOpen = false)}>
	<Field label="Unidades (positivo suma, negativo resta)">
		<input type="number" bind:value={adjust.quantity} />
	</Field>
	<Field label="Motivo">
		<textarea bind:value={adjust.reason} placeholder="Por ejemplo: avería en el muelle"></textarea>
	</Field>
	{#snippet actions()}
		<Button onclick={() => (adjustOpen = false)}>Cancelar</Button>
		<Button
			variant="primary"
			loading={busy}
			disabled={!adjust.reason || Number(adjust.quantity) === 0}
			onclick={submitAdjust}>Registrar ajuste</Button
		>
	{/snippet}
</Modal>

<Modal open={countOpen} title="Conteo físico" onclose={() => (countOpen = false)}>
	<p class="small text-muted">
		Escribe las unidades que contaste. Si la diferencia supera el 3 %, la IA alerta, pide un
		reconteo y propone el ajuste a gerencia.
	</p>
	<Field label="Unidades contadas">
		<input type="number" min="0" bind:value={countQty} />
	</Field>
	{#snippet actions()}
		<Button onclick={() => (countOpen = false)}>Cancelar</Button>
		<Button variant="primary" loading={busy} onclick={submitCount}>Registrar conteo</Button>
	{/snippet}
</Modal>

<style>
	h3 {
		margin: 0;
		font-size: var(--text-md);
	}

	.neg {
		color: var(--color-danger);
	}
</style>
