<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { page } from '$app/state';
	import { Plus, Trash2 } from '@lucide/svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { AiDecision, Customer, Product, SalesOrder, Warehouse } from '$lib/api/types';
	import { auth } from '$lib/stores/auth.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import StatusBadge from '$lib/components/domain/StatusBadge.svelte';
	import DecisionCard from '$lib/components/domain/DecisionCard.svelte';
	import Drawer from '$lib/components/ui/Drawer.svelte';
	import Modal from '$lib/components/ui/Modal.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Tabs from '$lib/components/ui/Tabs.svelte';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import { formatCop, formatDateTime, formatNumber } from '$lib/utils/format';
	import { SALES_STATUS } from '$lib/utils/labels';

	const SIZE = 25;
	let tab = $state('all');
	let orders = $state<SalesOrder[]>([]);
	let total = $state(0);
	let held = $state(0);
	let pageNo = $state(1);
	let warehouses = $state<Warehouse[]>([]);
	let filters = $state({ warehouse_id: '', status: '', q: '' });

	let selected = $state<SalesOrder | null>(null);
	let decisions = $state<AiDecision[]>([]);
	let busy = $state(false);

	let createOpen = $state(false);
	let customers = $state<Customer[]>([]);
	let products = $state<Product[]>([]);
	let draft = $state<{ customer_id: string; lines: { product_id: string; quantity: number }[] }>({
		customer_id: '',
		lines: [{ product_id: '', quantity: 1 }]
	});

	const loader = new Loader();

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function load() {
		return loader.run(fetchOrders);
	}

	async function fetchOrders() {
		const status = tab === 'held' ? 'on_hold' : filters.status || undefined;
		const res = await unwrap(
			api.GET('/sales-orders', {
				params: {
					query: {
						status,
						warehouse_id: filters.warehouse_id ? Number(filters.warehouse_id) : undefined,
						q: filters.q || undefined,
						page: pageNo,
						size: SIZE
					}
				}
			})
		);
		orders = res.items;
		total = res.total;
		held = (
			await unwrap(api.GET('/sales-orders', { params: { query: { status: 'on_hold', size: 1 } } }))
		).total;
	}

	async function open(id: number) {
		selected = await unwrap(api.GET('/sales-orders/{id}', { params: { path: { id } } }));
		decisions = (
			await unwrap(
				api.GET('/ai/decisions', {
					params: { query: { reference_type: 'sales_order', reference_id: id, size: 10 } }
				})
			)
		).items;
	}

	async function act(kind: 'release' | 'cancel') {
		if (!selected) return;
		busy = true;
		try {
			selected =
				kind === 'release'
					? await unwrap(
							api.POST('/sales-orders/{id}/release', { params: { path: { id: selected.id } } })
						)
					: await unwrap(
							api.POST('/sales-orders/{id}/cancel', {
								params: { path: { id: selected.id } },
								body: { reason: `Cancelado por ${auth.user?.full_name}` }
							})
						);
			toasts.success(
				kind === 'release' ? 'Pedido liberado: la IA crea las tareas de bodega' : 'Pedido cancelado'
			);
			await load();
		} catch (e) {
			toasts.error('No se pudo completar', e instanceof ApiError ? e.message : undefined);
		} finally {
			busy = false;
		}
	}

	async function openCreate() {
		if (customers.length === 0) {
			customers = (await unwrap(api.GET('/customers', { params: { query: { size: 200 } } }))).items;
			products = (await unwrap(api.GET('/products', { params: { query: { size: 200 } } }))).items;
		}
		draft = { customer_id: '', lines: [{ product_id: '', quantity: 1 }] };
		createOpen = true;
	}

	async function create() {
		busy = true;
		try {
			const order = await unwrap(
				api.POST('/sales-orders', {
					body: {
						customer_id: Number(draft.customer_id),
						items: draft.lines
							.filter((l) => l.product_id && l.quantity > 0)
							.map((l) => ({ product_id: Number(l.product_id), quantity: Number(l.quantity) }))
					}
				})
			);
			toasts.success(`Pedido ${order.number} creado`, SALES_STATUS[order.status]?.label);
			createOpen = false;
			await load();
			await open(order.id);
		} catch (e) {
			toasts.error('No se pudo crear el pedido', e instanceof ApiError ? e.message : undefined);
		} finally {
			busy = false;
		}
	}

	onMount(() => {
		void loader.run(async () => {
			warehouses = await unwrap(api.GET('/warehouses'));
			await fetchOrders();
			const id = Number(page.url.searchParams.get('id'));
			if (id) void open(id);
		});
		let timer: ReturnType<typeof setTimeout> | null = null;
		const off = live.on('sale.created', () => {
			if (timer || pageNo !== 1) return;
			timer = setTimeout(() => {
				timer = null;
				void load();
			}, 3000);
		});
		return () => {
			off();
			if (timer) clearTimeout(timer);
		};
	});

	let canManage = $derived(auth.hasRole('admin', 'manager'));
</script>

<svelte:head><title>Ventas · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Ventas</h1>
		<p>Pedidos de los clientes, su estado y los retenidos por anomalía para revisión</p>
	</div>
	{#if canManage}<Button variant="primary" onclick={openCreate}
			><Plus size={16} />Nuevo pedido</Button
		>{/if}
</div>

<Tabs
	tabs={[
		{ id: 'all', label: 'Todos' },
		{ id: 'held', label: 'Retenidos para revisión', count: held }
	]}
	active={tab}
	onchange={(id) => ((tab = id), (pageNo = 1), load())}
/>

{#if tab === 'all'}
	<form class="filters" onsubmit={(e) => (e.preventDefault(), (pageNo = 1), load())}>
		<Field label="Bodega">
			<select bind:value={filters.warehouse_id} onchange={() => ((pageNo = 1), load())}>
				<option value="">Todas</option>
				{#each warehouses as w (w.id)}<option value={String(w.id)}>{w.code}</option>{/each}
			</select>
		</Field>
		<Field label="Estado">
			<select bind:value={filters.status} onchange={() => ((pageNo = 1), load())}>
				<option value="">Todos</option>
				{#each Object.entries(SALES_STATUS) as [k, v] (k)}<option value={k}>{v.label}</option
					>{/each}
			</select>
		</Field>
		<Field label="Buscar">
			<input placeholder="Número o cliente" bind:value={filters.q} />
		</Field>
		<Button type="submit">Filtrar</Button>
	</form>
{/if}

<LoadState {loader} what="los pedidos" onretry={load}>
	<div class="table-wrap">
		<table class="data">
			<thead>
				<tr>
					<th>Pedido</th>
					<th>Fecha</th>
					<th>Cliente</th>
					<th>Ciudad</th>
					<th>Bodega</th>
					<th>Estado</th>
					<th class="num">Líneas</th>
					<th class="num">Total</th>
				</tr>
			</thead>
			<tbody>
				{#each orders as o (o.id)}
					<tr
						class="clickable"
						tabindex="0"
						onclick={() => open(o.id)}
						onkeydown={(e) => e.key === 'Enter' && open(o.id)}
					>
						<td class="mono">{o.number}</td>
						<td>{formatDateTime(o.ordered_at)}</td>
						<td>{o.customer_name}</td>
						<td>{o.customer_city}</td>
						<td>{o.warehouse_code}</td>
						<td>
							<StatusBadge map={SALES_STATUS} value={o.status} />
							{#if o.hold_reason && o.status === 'on_hold'}<div class="small text-muted">
									{o.hold_reason}
								</div>{/if}
						</td>
						<td class="num">{o.item_count}</td>
						<td class="num">{formatCop(o.total)}</td>
					</tr>
				{:else}
					<tr><td colspan="8" class="text-muted">No hay pedidos con estos filtros.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
	<Pagination page={pageNo} size={SIZE} {total} onchange={(p) => ((pageNo = p), load())} />
</LoadState>

<Drawer
	open={selected !== null}
	wide
	title={selected ? `Pedido ${selected.number}` : ''}
	subtitle={selected ? `${selected.customer_name} · ${selected.customer_city}` : ''}
	onclose={() => (selected = null)}
>
	{#if selected}
		<div class="row">
			<StatusBadge map={SALES_STATUS} value={selected.status} />
			<span class="text-muted small"
				>Segmento {selected.customer_segment} · bodega {selected.warehouse_code}</span
			>
		</div>
		{#if selected.hold_reason}
			<p class="notice">{selected.hold_reason}</p>
		{/if}
		<dl class="facts">
			<dt>Fecha</dt>
			<dd>{formatDateTime(selected.ordered_at)}</dd>
			<dt>Prometido</dt>
			<dd>{formatDateTime(selected.promised_at)}</dd>
			<dt>Subtotal</dt>
			<dd>{formatCop(selected.subtotal)}</dd>
			<dt>Descuento</dt>
			<dd>{formatCop(selected.discount)}</dd>
			<dt>Total</dt>
			<dd><strong>{formatCop(selected.total)}</strong></dd>
		</dl>
		<div class="table-wrap">
			<table class="data">
				<thead
					><tr
						><th>SKU</th><th>Producto</th><th class="num">Pedido</th><th class="num">Despachado</th
						><th class="num">Precio</th><th class="num">Total</th></tr
					></thead
				>
				<tbody>
					{#each selected.items ?? [] as l (l.id)}
						<tr>
							<td class="mono">{l.sku}</td>
							<td>{l.product_name}</td>
							<td class="num">{formatNumber(l.quantity)}</td>
							<td class="num" class:short={l.quantity_fulfilled < l.quantity}
								>{formatNumber(l.quantity_fulfilled)}</td
							>
							<td class="num">{formatCop(l.unit_price)}</td>
							<td class="num">{formatCop(l.line_total)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
		<section class="stack">
			<h3>Decisiones de la IA sobre este pedido</h3>
			{#each decisions as d (d.id)}
				<DecisionCard decision={d} compact />
			{:else}
				<p class="text-muted small">Sin decisiones registradas.</p>
			{/each}
		</section>
	{/if}
	{#snippet actions()}
		{#if selected && canManage}
			{#if selected.status === 'on_hold'}
				<Button variant="danger" loading={busy} onclick={() => act('cancel')}
					>Cancelar pedido</Button
				>
				<Button variant="primary" loading={busy} onclick={() => act('release')}
					>Liberar como demanda real</Button
				>
			{:else if ['created', 'picking', 'packed'].includes(selected.status)}
				<Button variant="danger" loading={busy} onclick={() => act('cancel')}
					>Cancelar pedido</Button
				>
			{/if}
		{/if}
	{/snippet}
</Drawer>

<Modal open={createOpen} title="Nuevo pedido de venta" onclose={() => (createOpen = false)}>
	<Field label="Cliente">
		<select bind:value={draft.customer_id}>
			<option value="">Elige un cliente</option>
			{#each customers as c (c.id)}<option value={String(c.id)}>{c.name} · {c.city}</option>{/each}
		</select>
	</Field>
	{#each draft.lines as line, i (i)}
		<div class="line">
			<Field label="Producto">
				<select bind:value={line.product_id}>
					<option value="">Producto</option>
					{#each products as p (p.id)}<option value={String(p.id)}>{p.sku} · {p.name}</option
						>{/each}
				</select>
			</Field>
			<Field label="Unidades">
				<input type="number" min="1" bind:value={line.quantity} />
			</Field>
			<button
				class="icon"
				aria-label="Quitar línea"
				onclick={() => (draft.lines = draft.lines.filter((_, j) => j !== i))}
			>
				<Trash2 size={16} />
			</button>
		</div>
	{/each}
	<Button
		size="sm"
		onclick={() => (draft.lines = [...draft.lines, { product_id: '', quantity: 1 }])}
		><Plus size={14} />Agregar línea</Button
	>
	{#snippet actions()}
		<Button onclick={() => (createOpen = false)}>Cancelar</Button>
		<Button variant="primary" loading={busy} disabled={!draft.customer_id} onclick={create}
			>Crear pedido</Button
		>
	{/snippet}
</Modal>

<style>
	h3 {
		margin: 0;
		font-size: var(--text-md);
	}

	.notice {
		margin: 0;
		padding: var(--space-3);
		background: var(--color-warning-bg);
		color: var(--color-warning);
		border-radius: var(--radius-md);
		font-size: var(--text-sm);
	}

	.short {
		color: var(--color-danger);
		font-weight: var(--weight-bold);
	}

	.line {
		display: grid;
		grid-template-columns: 1fr 110px auto;
		gap: var(--space-3);
		align-items: end;
	}

	.icon {
		border: none;
		background: none;
		cursor: pointer;
		color: var(--color-text-muted);
		padding: var(--space-2);
	}
</style>
