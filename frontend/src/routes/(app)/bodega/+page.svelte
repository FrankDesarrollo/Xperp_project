<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { Warehouse, WarehouseTask } from '$lib/api/types';
	import { auth } from '$lib/stores/auth.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import TaskCard from '$lib/components/domain/TaskCard.svelte';
	import Tabs from '$lib/components/ui/Tabs.svelte';
	import Modal from '$lib/components/ui/Modal.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import EmptyState from '$lib/components/ui/EmptyState.svelte';
	import { TASK_TYPE } from '$lib/utils/labels';

	let tab = $state('open');
	let tasks = $state<WarehouseTask[]>([]);
	let total = $state(0);
	let type = $state('');
	let warehouseId = $state('');
	let mine = $state(true);
	let warehouses = $state<Warehouse[]>([]);
	let busyId = $state<number | null>(null);

	let completing = $state<WarehouseTask | null>(null);
	let counted = $state(0);
	let received = $state<Record<number, number>>({});

	let isKeeper = $derived(auth.hasRole('warehouse'));

	const loader = new Loader();

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function load() {
		return loader.run(fetchTasks);
	}

	/** Primera carga y «Reintentar»: también las bodegas del filtro, por si fueron ellas las que fallaron. */
	async function fetchAll() {
		warehouses = await unwrap(api.GET('/warehouses'));
		await fetchTasks();
	}

	async function fetchTasks() {
		const res = await unwrap(
			api.GET('/warehouse-tasks', {
				params: {
					query: {
						assigned_to: isKeeper && mine ? 'me' : undefined,
						status: tab === 'open' ? 'open' : 'done',
						type: type || undefined,
						warehouse_id: warehouseId ? Number(warehouseId) : undefined,
						size: 60
					}
				}
			})
		);
		tasks = res.items;
		total = res.total;
	}

	async function start(t: WarehouseTask) {
		busyId = t.id;
		try {
			await unwrap(api.POST('/warehouse-tasks/{id}/start', { params: { path: { id: t.id } } }));
			toasts.success('Tarea iniciada', 'El bodeguero virtual ya no la toma');
			await load();
		} catch (e) {
			toasts.error('No se pudo iniciar', e instanceof ApiError ? e.message : undefined);
		} finally {
			busyId = null;
		}
	}

	function askComplete(t: WarehouseTask) {
		if (t.type === 'count') {
			counted = t.lines[0]?.quantity ?? 0;
			completing = t;
		} else if (t.type === 'receive') {
			received = Object.fromEntries(t.lines.map((l) => [l.product_id, l.quantity]));
			completing = t;
		} else {
			void complete(t);
		}
	}

	async function complete(t: WarehouseTask) {
		busyId = t.id;
		try {
			const body =
				t.type === 'count'
					? { counted_qty: Number(counted) }
					: t.type === 'receive'
						? {
								lines: Object.entries(received).map(([id, q]) => ({
									product_id: Number(id),
									quantity: Number(q)
								}))
							}
						: undefined;
			await unwrap(
				api.POST('/warehouse-tasks/{id}/complete', { params: { path: { id: t.id } }, body })
			);
			toasts.success('Tarea terminada', t.title);
			completing = null;
			await load();
		} catch (e) {
			toasts.error('No se pudo terminar', e instanceof ApiError ? e.message : undefined);
		} finally {
			busyId = null;
		}
	}

	onMount(() => {
		void loader.run(fetchAll);
		let timer: ReturnType<typeof setTimeout> | null = null;
		const refresh = () => {
			if (timer) return;
			timer = setTimeout(() => {
				timer = null;
				void load();
			}, 2500);
		};
		const offs = [live.on('task.created', refresh), live.on('task.changed', refresh)];
		return () => {
			offs.forEach((o) => o());
			if (timer) clearTimeout(timer);
		};
	});
</script>

<svelte:head><title>Bodega · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Bodega</h1>
		<p>
			{isKeeper
				? 'Tus tareas de alistamiento, empaque, recepción, conteo y traslados'
				: 'Tareas de bodega de todas las bodegas'}
		</p>
	</div>
</div>

<Tabs
	tabs={[
		{ id: 'open', label: 'Pendientes', count: tab === 'open' ? total : undefined },
		{ id: 'done', label: 'Terminadas' }
	]}
	active={tab}
	onchange={(id) => ((tab = id), load())}
/>

<div class="filters">
	<Field label="Tipo">
		<select bind:value={type} onchange={load}>
			<option value="">Todos</option>
			{#each Object.entries(TASK_TYPE) as [k, v] (k)}<option value={k}>{v}</option>{/each}
		</select>
	</Field>
	{#if !isKeeper}
		<Field label="Bodega">
			<select bind:value={warehouseId} onchange={load}>
				<option value="">Todas</option>
				{#each warehouses as w (w.id)}<option value={String(w.id)}>{w.code}</option>{/each}
			</select>
		</Field>
	{:else}
		<label class="row small check"
			><input type="checkbox" bind:checked={mine} onchange={load} /> Solo las asignadas a mí</label
		>
	{/if}
</div>

<LoadState {loader} what="las tareas" onretry={() => loader.run(fetchAll)}>
	{#if tasks.length === 0}
		<EmptyState
			title={tab === 'open' ? 'No hay tareas pendientes' : 'Todavía no hay tareas terminadas'}
		>
			Las tareas las crea la IA cuando entra un pedido, llega mercancía o hay un conteo o traslado.
		</EmptyState>
	{:else}
		<div class="tasks">
			{#each tasks as t (t.id)}
				<TaskCard
					task={t}
					busy={busyId === t.id}
					onstart={tab === 'open' ? start : undefined}
					oncomplete={tab === 'open' ? askComplete : undefined}
				/>
			{/each}
		</div>
	{/if}
</LoadState>

<Modal
	open={completing !== null}
	title={completing?.title ?? ''}
	onclose={() => (completing = null)}
>
	{#if completing?.type === 'count'}
		<p class="small text-muted">
			Cuenta las unidades físicas y escríbelas. Si no coincide con el kardex, la IA lo revisa.
		</p>
		<Field label="Unidades contadas"><input type="number" min="0" bind:value={counted} /></Field>
	{:else if completing}
		<p class="small text-muted">Confirma lo que llegó. Si llegó menos, corrige la cantidad.</p>
		{#each completing.lines as l (l.product_id)}
			<Field label="{l.sku} · {l.product_name}">
				<input type="number" min="0" bind:value={received[l.product_id]} />
			</Field>
		{/each}
	{/if}
	{#snippet actions()}
		<Button onclick={() => (completing = null)}>Cancelar</Button>
		<Button
			variant="primary"
			loading={busyId === completing?.id}
			onclick={() => completing && complete(completing)}>Terminar tarea</Button
		>
	{/snippet}
</Modal>

<style>
	.tasks {
		display: grid;
		gap: var(--space-4);
		grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
	}

	.check {
		align-self: center;
	}
</style>
