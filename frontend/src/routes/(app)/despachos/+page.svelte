<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { FileText } from '@lucide/svelte';
	import { api, unwrap, openPdf } from '$lib/api/client';
	import type { Carrier, Shipment } from '$lib/api/types';
	import { live } from '$lib/stores/live.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import StatusBadge from '$lib/components/domain/StatusBadge.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import { formatCop, formatDateTime, formatNumber, formatPct } from '$lib/utils/format';
	import { SHIPMENT_STATUS } from '$lib/utils/labels';

	const SIZE = 25;
	let shipments = $state<Shipment[]>([]);
	let carriers = $state<Carrier[]>([]);
	let total = $state(0);
	let pageNo = $state(1);
	let status = $state('');
	let carrierId = $state('');

	const loader = new Loader();

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function load() {
		return loader.run(fetchShipments);
	}

	async function fetchShipments() {
		const res = await unwrap(
			api.GET('/shipments', {
				params: {
					query: {
						status: status || undefined,
						carrier_id: carrierId ? Number(carrierId) : undefined,
						page: pageNo,
						size: SIZE
					}
				}
			})
		);
		shipments = res.items;
		total = res.total;
	}

	onMount(() => {
		void loader.run(async () => {
			carriers = await unwrap(api.GET('/carriers'));
			await fetchShipments();
		});
		let timer: ReturnType<typeof setTimeout> | null = null;
		const off = live.on('shipment.changed', () => {
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
</script>

<svelte:head><title>Despachos · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Despachos</h1>
		<p>Envíos con guía; la IA elige la transportadora por costo, tiempo y cumplimiento</p>
	</div>
</div>

<div class="carriers">
	{#each carriers as c (c.id)}
		<div class="card carrier">
			<div class="row">
				<strong>{c.name}</strong><span class="spacer"></span><Badge tone="info">{c.code}</Badge>
			</div>
			<span class="small text-muted"
				>{c.national ? 'Cobertura nacional' : c.coverage_cities.join(', ')}</span
			>
			<span class="small"
				>{formatCop(c.base_rate)} + {formatCop(c.rate_per_kg)}/kg · cumplimiento {formatPct(
					c.on_time_rate,
					true,
					0
				)}</span
			>
		</div>
	{/each}
</div>

<div class="filters">
	<Field label="Estado">
		<select bind:value={status} onchange={() => ((pageNo = 1), load())}>
			<option value="">Todos</option>
			{#each Object.entries(SHIPMENT_STATUS) as [k, v] (k)}<option value={k}>{v.label}</option
				>{/each}
		</select>
	</Field>
	<Field label="Transportadora">
		<select bind:value={carrierId} onchange={() => ((pageNo = 1), load())}>
			<option value="">Todas</option>
			{#each carriers as c (c.id)}<option value={String(c.id)}>{c.name}</option>{/each}
		</select>
	</Field>
</div>

<LoadState {loader} what="los envíos" onretry={load}>
	<div class="table-wrap">
		<table class="data">
			<thead>
				<tr
					><th>Guía</th><th>Pedido</th><th>Cliente</th><th>Ruta</th><th>Transportadora</th><th
						>Estado</th
					><th class="num">Peso</th><th class="num">Flete</th><th>Entrega estimada</th><th></th></tr
				>
			</thead>
			<tbody>
				{#each shipments as s (s.id)}
					<tr>
						<td class="mono">{s.tracking_number}</td>
						<td class="mono">{s.sales_order_number}</td>
						<td>{s.customer_name ?? '—'}</td>
						<td>{s.origin_city} → {s.destination_city}</td>
						<td>{s.carrier_name} <span class="text-muted small">{s.service}</span></td>
						<td><StatusBadge map={SHIPMENT_STATUS} value={s.status} /></td>
						<td class="num">{formatNumber(s.weight_kg, 1)} kg</td>
						<td class="num">{formatCop(s.cost)}</td>
						<td
							>{s.delivered_at
								? `Entregado ${formatDateTime(s.delivered_at)}`
								: formatDateTime(s.eta)}</td
						>
						<td>
							<button
								class="link"
								onclick={() =>
									openPdf(`/shipments/${s.id}/label`).catch(() =>
										toasts.error('No se pudo abrir la guía')
									)}
								aria-label="Guía en PDF"
							>
								<FileText size={16} />
							</button>
						</td>
					</tr>
				{:else}
					<tr><td colspan="10" class="text-muted">Todavía no hay envíos.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
	<Pagination page={pageNo} size={SIZE} {total} onchange={(p) => ((pageNo = p), load())} />
</LoadState>

<style>
	.carriers {
		display: grid;
		gap: var(--space-4);
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
		margin-bottom: var(--space-5);
	}

	.carrier {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-4);
	}

	.link {
		border: none;
		background: none;
		color: var(--color-primary);
		cursor: pointer;
	}
</style>
