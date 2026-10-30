<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { page } from '$app/state';
	import { FileText, Plus, Trash2 } from '@lucide/svelte';
	import { api, unwrap, ApiError, openPdf } from '$lib/api/client';
	import type {
		AccountPayable,
		AiDecision,
		Budget,
		PurchaseOrder,
		Supplier,
		SupplierOffer,
		Warehouse
	} from '$lib/api/types';
	import { auth } from '$lib/stores/auth.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import StatusBadge from '$lib/components/domain/StatusBadge.svelte';
	import DecisionCard from '$lib/components/domain/DecisionCard.svelte';
	import Timeline from '$lib/components/domain/Timeline.svelte';
	import Drawer from '$lib/components/ui/Drawer.svelte';
	import Modal from '$lib/components/ui/Modal.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Tabs from '$lib/components/ui/Tabs.svelte';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import EChart from '$lib/components/charts/EChart.svelte';
	import { barOption } from '$lib/components/charts/options';
	import {
		formatCop,
		formatDate,
		formatDateTime,
		formatNumber,
		formatPct
	} from '$lib/utils/format';
	import { PO_STATUS, type Tone } from '$lib/utils/labels';

	const SIZE = 25;
	let tab = $state('orders');
	let orders = $state<PurchaseOrder[]>([]);
	let total = $state(0);
	let pageNo = $state(1);
	let status = $state('');
	let suppliers = $state<Supplier[]>([]);
	let warehouses = $state<Warehouse[]>([]);
	let budgets = $state<Budget[]>([]);
	let payables = $state<AccountPayable[]>([]);

	let selected = $state<PurchaseOrder | null>(null);
	let decisions = $state<AiDecision[]>([]);
	let supplier = $state<Supplier | null>(null);
	let supplierOffers = $state<SupplierOffer[]>([]);
	let busy = $state(false);

	let createOpen = $state(false);
	let createOffers = $state<SupplierOffer[]>([]);
	let draft = $state<{
		supplier_id: string;
		warehouse_id: string;
		send: boolean;
		lines: { product_id: string; quantity: number }[];
	}>({
		supplier_id: '',
		warehouse_id: '',
		send: true,
		lines: [{ product_id: '', quantity: 0 }]
	});

	const loader = new Loader();

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function load() {
		return loader.run(fetchOrders);
	}

	async function fetchOrders() {
		const res = await unwrap(
			api.GET('/purchase-orders', {
				params: { query: { status: status || undefined, page: pageNo, size: SIZE } }
			})
		);
		orders = res.items;
		total = res.total;
	}

	async function open(id: number) {
		selected = await unwrap(api.GET('/purchase-orders/{id}', { params: { path: { id } } }));
		decisions = (
			await unwrap(
				api.GET('/ai/decisions', {
					params: { query: { reference_type: 'purchase_order', reference_id: id, size: 10 } }
				})
			)
		).items;
	}

	async function act(kind: 'send' | 'cancel') {
		if (!selected) return;
		busy = true;
		try {
			selected =
				kind === 'send'
					? await unwrap(
							api.POST('/purchase-orders/{id}/send', { params: { path: { id: selected.id } } })
						)
					: await unwrap(
							api.POST('/purchase-orders/{id}/cancel', {
								params: { path: { id: selected.id } },
								body: {}
							})
						);
			toasts.success(kind === 'send' ? 'OC enviada al proveedor' : 'OC cancelada');
			await load();
		} catch (e) {
			toasts.error('No se pudo completar', e instanceof ApiError ? e.message : undefined);
		} finally {
			busy = false;
		}
	}

	async function openSupplier(s: Supplier) {
		supplier = s;
		supplierOffers = await unwrap(
			api.GET('/suppliers/{id}/offers', { params: { path: { id: s.id } } })
		);
	}

	async function loadFinance() {
		budgets = await unwrap(api.GET('/budgets'));
		payables = (await unwrap(api.GET('/accounts-payable', { params: { query: { size: 50 } } })))
			.items;
	}

	async function pickSupplier() {
		draft.lines = [{ product_id: '', quantity: 0 }];
		createOffers = draft.supplier_id
			? await unwrap(
					api.GET('/suppliers/{id}/offers', { params: { path: { id: Number(draft.supplier_id) } } })
				)
			: [];
	}

	async function create() {
		busy = true;
		try {
			const po = await unwrap(
				api.POST('/purchase-orders', {
					body: {
						supplier_id: Number(draft.supplier_id),
						warehouse_id: Number(draft.warehouse_id),
						send: draft.send,
						items: draft.lines
							.filter((l) => l.product_id && l.quantity > 0)
							.map((l) => ({ product_id: Number(l.product_id), quantity: Number(l.quantity) }))
					}
				})
			);
			toasts.success(
				`${po.number} creada`,
				draft.send ? 'Enviada al proveedor' : 'Quedó en borrador'
			);
			createOpen = false;
			await load();
			await open(po.id);
		} catch (e) {
			toasts.error('No se pudo crear la OC', e instanceof ApiError ? e.message : undefined);
		} finally {
			busy = false;
		}
	}

	onMount(() => {
		void loader.run(async () => {
			[suppliers, warehouses] = await Promise.all([
				unwrap(api.GET('/suppliers')),
				unwrap(api.GET('/warehouses'))
			]);
			await fetchOrders();
			const id = Number(page.url.searchParams.get('oc') ?? page.url.searchParams.get('id'));
			if (id) void open(id);
		});
		const off = live.on('purchase_order.changed', () => {
			if (pageNo === 1) void load();
		});
		return off;
	});

	$effect(() => {
		if (tab === 'budget') void loadFinance();
	});

	function eventTone(s: string): Tone {
		return PO_STATUS[s]?.tone ?? 'neutral';
	}

	let canBuy = $derived(auth.hasRole('admin', 'purchasing', 'manager'));
	let caseOf = $derived(
		Object.fromEntries(createOffers.map((o) => [String(o.product_id), o])) as Record<
			string,
			SupplierOffer
		>
	);
	let draftTotal = $derived(
		draft.lines.reduce(
			(sum, l) =>
				sum + (caseOf[l.product_id] ? Number(caseOf[l.product_id]!.unit_cost) * l.quantity : 0),
			0
		)
	);
</script>

<svelte:head><title>Compras · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Compras</h1>
		<p>Órdenes de compra, proveedores, ofertas y presupuesto del mes</p>
	</div>
	{#if canBuy}<Button
			variant="primary"
			onclick={() => (
				(createOpen = true),
				(draft = {
					supplier_id: '',
					warehouse_id: '',
					send: true,
					lines: [{ product_id: '', quantity: 0 }]
				}),
				(createOffers = [])
			)}><Plus size={16} />Nueva OC</Button
		>{/if}
</div>

<Tabs
	tabs={[
		{ id: 'orders', label: 'Órdenes de compra', count: total },
		{ id: 'suppliers', label: 'Proveedores', count: suppliers.length },
		{ id: 'budget', label: 'Presupuesto y cuentas por pagar' }
	]}
	active={tab}
	onchange={(id) => (tab = id)}
/>

<LoadState {loader} what="las órdenes de compra" onretry={load}>
	{#if tab === 'orders'}
		<div class="filters">
			<Field label="Estado">
				<select bind:value={status} onchange={() => ((pageNo = 1), load())}>
					<option value="">Todas</option>
					<option value="open">Abiertas</option>
					{#each Object.entries(PO_STATUS) as [k, v] (k)}{#if k !== 'delivered'}<option value={k}
								>{v.label}</option
							>{/if}{/each}
				</select>
			</Field>
		</div>
		<div class="table-wrap">
			<table class="data">
				<thead>
					<tr
						><th>OC</th><th>Creada</th><th>Proveedor</th><th>Bodega</th><th>Estado</th><th
							>Origen</th
						><th>Llega</th><th class="num">Total</th></tr
					>
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
							<td>{formatDateTime(o.created_at)}</td>
							<td>{o.supplier_code} · {o.supplier_name}</td>
							<td>{o.warehouse_code}</td>
							<td><StatusBadge map={PO_STATUS} value={o.status} /></td>
							<td
								>{#if o.created_by_ai}<Badge tone="ai">IA</Badge>{:else}<span class="small"
										>{o.created_by_name ?? 'Usuario'}</span
									>{/if}</td
							>
							<td>{formatDateTime(o.expected_at)}</td>
							<td class="num">{formatCop(o.total)}</td>
						</tr>
					{:else}
						<tr><td colspan="8" class="text-muted">No hay órdenes de compra.</td></tr>
					{/each}
				</tbody>
			</table>
		</div>
		<Pagination page={pageNo} size={SIZE} {total} onchange={(p) => ((pageNo = p), load())} />
	{:else if tab === 'suppliers'}
		<div class="table-wrap">
			<table class="data">
				<thead>
					<tr
						><th>Código</th><th>Proveedor</th><th>Ciudad</th><th>Entrega</th><th class="num"
							>Pedido mínimo</th
						><th>Plazo</th><th class="num">Cumplimiento</th><th class="num">Ofertas</th></tr
					>
				</thead>
				<tbody>
					{#each suppliers as s (s.id)}
						<tr
							class="clickable"
							tabindex="0"
							onclick={() => openSupplier(s)}
							onkeydown={(e) => e.key === 'Enter' && openSupplier(s)}
						>
							<td class="mono">{s.code}</td>
							<td>{s.name}</td>
							<td>{s.city}</td>
							<td class="small"
								>{s.lead_times
									.map((l) => `${l.warehouse_code} ${l.lead_time_days} d`)
									.join(' · ')}</td
							>
							<td class="num">{formatCop(s.min_order_amount)}</td>
							<td>{s.payment_terms_days === 0 ? 'Contado' : `${s.payment_terms_days} días`}</td>
							<td class="num">{formatPct(s.on_time_rate, true, 0)}</td>
							<td class="num">{s.offer_count}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{:else}
		<div class="grid-2">
			<div class="card">
				<h2>Uso del presupuesto</h2>
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
			</div>
			<div class="table-wrap">
				<table class="data">
					<thead
						><tr
							><th>Categoría</th><th class="num">Presupuesto</th><th class="num">Comprometido</th
							><th class="num">Gastado</th><th class="num">Disponible</th></tr
						></thead
					>
					<tbody>
						{#each budgets as b (b.id)}
							<tr>
								<td>{b.category_name} <span class="text-muted small">{b.period}</span></td>
								<td class="num">{formatCop(b.amount)}</td>
								<td class="num">{formatCop(b.committed)}</td>
								<td class="num">{formatCop(b.spent)}</td>
								<td class="num" class:neg={Number(b.available) < 0}>{formatCop(b.available)}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</div>
		<h2 class="section-title">Cuentas por pagar</h2>
		<div class="table-wrap">
			<table class="data">
				<thead
					><tr
						><th>OC</th><th>Proveedor</th><th>Vence</th><th>Estado</th><th class="num">Monto</th
						></tr
					></thead
				>
				<tbody>
					{#each payables as p (p.id)}
						<tr>
							<td class="mono">{p.purchase_order_number}</td>
							<td>{p.supplier_name}</td>
							<td>{formatDate(p.due_date)}</td>
							<td
								><Badge tone={p.status === 'paid' ? 'success' : 'warning'}
									>{p.status === 'paid' ? 'Pagada' : 'Abierta'}</Badge
								></td
							>
							<td class="num">{formatCop(p.amount)}</td>
						</tr>
					{:else}
						<tr
							><td colspan="5" class="text-muted"
								>Aún no hay cuentas por pagar (se crean al recibir la mercancía).</td
							></tr
						>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
</LoadState>

<Drawer
	open={selected !== null}
	wide
	title={selected ? `Orden de compra ${selected.number}` : ''}
	subtitle={selected ? `${selected.supplier_name} → ${selected.warehouse_code}` : ''}
	onclose={() => (selected = null)}
>
	{#if selected}
		<div class="row">
			<StatusBadge map={PO_STATUS} value={selected.status} />
			{#if selected.created_by_ai}<Badge tone="ai">Creada por la IA</Badge>{/if}
			{#if selected.replaces_po_number}<Badge tone="info"
					>Reemplaza a {selected.replaces_po_number}</Badge
				>{/if}
		</div>
		{#if selected.status_reason}<p class="notice">{selected.status_reason}</p>{/if}
		<dl class="facts">
			<dt>Total</dt>
			<dd><strong>{formatCop(selected.total)}</strong></dd>
			<dt>Enviada</dt>
			<dd>{formatDateTime(selected.sent_at)}</dd>
			<dt>Confirmada</dt>
			<dd>{formatDateTime(selected.confirmed_at)}</dd>
			<dt>Llegada estimada</dt>
			<dd>{formatDateTime(selected.expected_at)}</dd>
			<dt>Recibida</dt>
			<dd>{formatDateTime(selected.received_at)}</dd>
			<dt>Id del proveedor</dt>
			<dd class="mono">{selected.external_id ?? '—'}</dd>
		</dl>
		<div class="table-wrap">
			<table class="data">
				<thead
					><tr
						><th>SKU</th><th>Producto</th><th class="num">Pedido</th><th class="num">Recibido</th
						><th class="num">Costo</th><th class="num">Total</th></tr
					></thead
				>
				<tbody>
					{#each selected.items ?? [] as l (l.id)}
						<tr>
							<td class="mono">{l.sku}</td>
							<td>{l.product_name}</td>
							<td class="num">{formatNumber(l.quantity)}</td>
							<td class="num">{formatNumber(l.quantity_received)}</td>
							<td class="num">{formatCop(l.unit_cost)}</td>
							<td class="num">{formatCop(l.line_total)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
		<section class="stack">
			<h3>Línea de tiempo</h3>
			<Timeline
				items={(selected.events ?? []).map((e) => ({
					title: PO_STATUS[e.status]?.label ?? e.status,
					note: e.note,
					at: e.occurred_at,
					tone: eventTone(e.status)
				}))}
			/>
		</section>
		<section class="stack">
			<h3>Decisiones de la IA</h3>
			{#each decisions as d (d.id)}
				<DecisionCard decision={d} compact />
			{:else}
				<p class="text-muted small">Sin decisiones registradas.</p>
			{/each}
		</section>
	{/if}
	{#snippet actions()}
		{#if selected}
			<Button
				onclick={() =>
					openPdf(`/purchase-orders/${selected!.id}/pdf`).catch(() =>
						toasts.error('No se pudo abrir el PDF')
					)}
			>
				<FileText size={16} />PDF
			</Button>
			{#if canBuy && ['draft', 'pending_approval', 'sent', 'confirmed', 'delayed', 'rejected'].includes(selected.status)}
				<Button variant="danger" loading={busy} onclick={() => act('cancel')}>Cancelar OC</Button>
			{/if}
			{#if canBuy && selected.status === 'draft'}
				<Button variant="primary" loading={busy} onclick={() => act('send')}
					>Enviar al proveedor</Button
				>
			{/if}
		{/if}
	{/snippet}
</Drawer>

<Drawer
	open={supplier !== null}
	wide
	title={supplier?.name ?? ''}
	subtitle={supplier ? `${supplier.code} · ${supplier.city} · ${supplier.email}` : ''}
	onclose={() => (supplier = null)}
>
	{#if supplier}
		<div class="table-wrap">
			<table class="data">
				<thead
					><tr
						><th>SKU</th><th>Producto</th><th class="num">Costo proveedor</th><th class="num"
							>Costo de lista</th
						><th class="num">Mínimo</th><th>Preferido</th><th>Precio actualizado</th></tr
					></thead
				>
				<tbody>
					{#each supplierOffers as o (o.product_id)}
						<tr>
							<td class="mono">{o.sku}</td>
							<td>{o.product_name}</td>
							<td class="num">{formatCop(o.unit_cost)}</td>
							<td class="num">{formatCop(o.list_cost)}</td>
							<td class="num">{o.min_order_cases} × {o.units_per_case}</td>
							<td
								>{#if o.is_preferred}<Badge tone="success">Preferido</Badge>{/if}</td
							>
							<td>{formatDateTime(o.price_updated_at)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
</Drawer>

<Modal open={createOpen} title="Nueva orden de compra" onclose={() => (createOpen = false)}>
	<div class="grid2">
		<Field label="Proveedor">
			<select bind:value={draft.supplier_id} onchange={pickSupplier}>
				<option value="">Elige un proveedor</option>
				{#each suppliers as s (s.id)}<option value={String(s.id)}>{s.code} · {s.name}</option
					>{/each}
			</select>
		</Field>
		<Field label="Bodega">
			<select bind:value={draft.warehouse_id}>
				<option value="">Elige una bodega</option>
				{#each warehouses as w (w.id)}<option value={String(w.id)}>{w.code}</option>{/each}
			</select>
		</Field>
	</div>
	{#each draft.lines as line, i (i)}
		<div class="line">
			<Field label="Producto">
				<select bind:value={line.product_id} disabled={createOffers.length === 0}>
					<option value="">Producto</option>
					{#each createOffers as o (o.product_id)}<option value={String(o.product_id)}
							>{o.sku} · {o.product_name} · {formatCop(o.unit_cost)}</option
						>{/each}
				</select>
			</Field>
			<Field
				label="Unidades"
				hint={caseOf[line.product_id]
					? `Caja de ${caseOf[line.product_id]!.units_per_case}`
					: undefined}
			>
				<input type="number" min="1" bind:value={line.quantity} />
			</Field>
			<button
				class="icon"
				aria-label="Quitar línea"
				onclick={() => (draft.lines = draft.lines.filter((_, j) => j !== i))}
				><Trash2 size={16} /></button
			>
		</div>
	{/each}
	<div class="row">
		<Button
			size="sm"
			onclick={() => (draft.lines = [...draft.lines, { product_id: '', quantity: 0 }])}
			><Plus size={14} />Agregar línea</Button
		>
		<span class="spacer"></span>
		<strong>{formatCop(draftTotal)}</strong>
	</div>
	<label class="row small"
		><input type="checkbox" bind:checked={draft.send} /> Enviar al proveedor al crearla</label
	>
	{#snippet actions()}
		<Button onclick={() => (createOpen = false)}>Cancelar</Button>
		<Button
			variant="primary"
			loading={busy}
			disabled={!draft.supplier_id || !draft.warehouse_id}
			onclick={create}>Crear OC</Button
		>
	{/snippet}
</Modal>

<style>
	h3 {
		margin: 0;
		font-size: var(--text-md);
	}

	.section-title {
		margin-top: var(--space-6);
		font-size: var(--text-lg);
	}

	.notice {
		margin: 0;
		padding: var(--space-3);
		background: var(--color-warning-bg);
		color: var(--color-warning);
		border-radius: var(--radius-md);
		font-size: var(--text-sm);
	}

	.neg {
		color: var(--color-danger);
	}

	.grid2 {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-3);
	}

	.line {
		display: grid;
		grid-template-columns: 1fr 120px auto;
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
