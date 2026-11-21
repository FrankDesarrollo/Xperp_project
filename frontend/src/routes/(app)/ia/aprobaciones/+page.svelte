<script lang="ts">
	import { onMount } from 'svelte';
	import { CheckCheck } from '@lucide/svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { Approval } from '$lib/api/types';
	import { auth } from '$lib/stores/auth.svelte';
	import { approvals } from '$lib/stores/approvals.svelte';
	import { liveRefresh } from '$lib/utils/live-refresh';
	import { toasts } from '$lib/stores/toasts.svelte';
	import ApprovalCard from '$lib/components/domain/ApprovalCard.svelte';
	import Tabs from '$lib/components/ui/Tabs.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import EmptyState from '$lib/components/ui/EmptyState.svelte';
	import { formatCop } from '$lib/utils/format';
	import { APPROVAL_TYPES } from '$lib/utils/labels';

	const SIZE = 25;
	const DECIDERS: Record<string, string[]> = {
		purchasing: ['purchasing', 'manager', 'admin'],
		manager: ['manager', 'admin'],
		admin: ['admin']
	};

	let tab = $state('pending');
	let type = $state('');
	let items = $state<Approval[]>([]);
	let total = $state(0);
	let pageNo = $state(1);
	let busyId = $state<number | null>(null);
	let batchBusy = $state(false);
	let batchComment = $state('');
	let selectedIds = $state<number[]>([]);

	const loader = new Loader();

	let decidable = $derived(items.filter((a) => a.status === 'pending' && canDecide(a)));
	let selected = $derived(items.filter((a) => selectedIds.includes(a.id)));
	let selectedAmount = $derived(
		selected.reduce((sum, a) => sum + Number(a.decision.amount ?? 0), 0)
	);
	let allSelected = $derived(
		decidable.length > 0 && decidable.every((a) => selectedIds.includes(a.id))
	);

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function load() {
		return loader.run(fetchApprovals);
	}

	async function fetchApprovals() {
		const res = await unwrap(
			api.GET('/approvals', {
				params: {
					query: {
						status: tab === 'all' ? undefined : tab,
						mine: tab === 'pending',
						decision_type: type || undefined,
						page: pageNo,
						size: SIZE
					}
				}
			})
		);
		items = res.items;
		total = res.total;
		selectedIds = selectedIds.filter((id) => res.items.some((a) => a.id === id));
	}

	async function decide(a: Approval, comment: string, approve: boolean) {
		busyId = a.id;
		try {
			const body = comment ? { comment } : {};
			if (approve) {
				await unwrap(api.POST('/approvals/{id}/approve', { params: { path: { id: a.id } }, body }));
				toasts.success('Aprobada', a.decision.title ?? undefined);
			} else {
				await unwrap(api.POST('/approvals/{id}/reject', { params: { path: { id: a.id } }, body }));
				toasts.success('Rechazada', a.decision.title ?? undefined);
			}
			await load();
			void approvals.refresh();
		} catch (e) {
			toasts.error('No se pudo resolver', e instanceof ApiError ? e.message : undefined);
		} finally {
			busyId = null;
		}
	}

	/** Aprueba las marcadas. Cada una se decide por separado: una que falla no frena a las demás. */
	async function approveSelected() {
		batchBusy = true;
		try {
			const res = await unwrap(
				api.POST('/approvals/batch-approve', {
					body: { ids: selectedIds, comment: batchComment || null }
				})
			);
			if (res.approved.length > 0) {
				toasts.success(
					`${res.approved.length} aprobadas`,
					`Por ${formatCop(res.approved_amount)} en total`
				);
			}
			if (res.failed.length > 0) {
				toasts.error(`${res.failed.length} no se pudieron aprobar`, res.failed[0]?.message);
			}
			selectedIds = [];
			batchComment = '';
			await load();
		} catch (e) {
			toasts.error('No se pudo aprobar el lote', e instanceof ApiError ? e.message : undefined);
		} finally {
			batchBusy = false;
		}
	}

	function toggle(a: Approval, on: boolean) {
		selectedIds = on ? [...selectedIds, a.id] : selectedIds.filter((id) => id !== a.id);
	}

	function toggleAll() {
		selectedIds = allSelected ? [] : decidable.map((a) => a.id);
	}

	function canDecide(a: Approval): boolean {
		return (DECIDERS[a.requested_role] ?? ['admin']).includes(auth.user?.role ?? '');
	}

	function apply() {
		pageNo = 1;
		selectedIds = [];
		void load();
	}

	onMount(() => {
		void load();
		return liveRefresh(['approval.requested'], () => void load(), {
			delay: 1000,
			when: () => tab === 'pending'
		});
	});
</script>

<svelte:head><title>Aprobaciones · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Aprobaciones</h1>
		<p>
			La IA prepara y explica; una persona decide. Todo lo que mueve dinero o activos pasa por aquí:
			órdenes de compra, guías de despacho, reasignaciones, traslados y ajustes. Vence a las 24
			horas simuladas.
		</p>
	</div>
</div>

<Tabs
	tabs={[
		{ id: 'pending', label: 'Por decidir', count: tab === 'pending' ? total : undefined },
		{ id: 'approved', label: 'Aprobadas' },
		{ id: 'rejected', label: 'Rechazadas' },
		{ id: 'expired', label: 'Vencidas' }
	]}
	active={tab}
	onchange={(id) => ((tab = id), apply())}
/>

<div class="filters">
	<Field label="Tipo">
		<select bind:value={type} onchange={apply}>
			<option value="">Todas</option>
			{#each APPROVAL_TYPES as t (t.id)}<option value={t.id}>{t.label}</option>{/each}
		</select>
	</Field>
	{#if tab === 'pending' && decidable.length > 0}
		<label class="row small check"
			><input type="checkbox" checked={allSelected} onchange={toggleAll} /> Marcar las {decidable.length}
			de esta página</label
		>
	{/if}
</div>

{#if selected.length > 0}
	<div class="batch" role="region" aria-label="Aprobación en lote">
		<p class="small">
			<strong>{selected.length} marcadas</strong> · {formatCop(selectedAmount)} en total. Cada una se
			aprueba con sus propias reglas.
		</p>
		<input
			placeholder="Comentario para todas (opcional)"
			bind:value={batchComment}
			aria-label="Comentario de la aprobación en lote"
		/>
		<Button variant="primary" loading={batchBusy} onclick={approveSelected}
			><CheckCheck size={16} />Aprobar {selected.length}</Button
		>
	</div>
{/if}

<LoadState {loader} what="las aprobaciones" onretry={load}>
	{#if items.length === 0}
		<EmptyState
			title={tab === 'pending'
				? 'No tienes aprobaciones pendientes'
				: 'No hay aprobaciones en esta lista'}
		>
			Toda compra, reasignación, cancelación, traslado, ajuste de inventario o guía de despacho que
			recomiende la IA llega aquí con su impacto. Si nadie la decide en 24 horas simuladas, vence.
		</EmptyState>
	{:else}
		<div class="list">
			{#each items as a (a.id)}
				<ApprovalCard
					approval={a}
					canDecide={canDecide(a)}
					busy={busyId === a.id}
					selected={selectedIds.includes(a.id)}
					onselect={tab === 'pending' ? toggle : undefined}
					onapprove={(x, c) => decide(x, c, true)}
					onreject={(x, c) => decide(x, c, false)}
				/>
			{/each}
		</div>
		<Pagination page={pageNo} size={SIZE} {total} onchange={(p) => ((pageNo = p), load())} />
	{/if}
</LoadState>

<style>
	.list {
		display: grid;
		gap: var(--space-4);
	}

	.check {
		align-self: center;
	}

	.batch {
		position: sticky;
		top: var(--space-2);
		z-index: 2;
		display: flex;
		gap: var(--space-3);
		flex-wrap: wrap;
		align-items: center;
		padding: var(--space-3) var(--space-4);
		margin-bottom: var(--space-4);
		background: var(--color-surface);
		border: 1px solid var(--color-primary);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
	}

	.batch p {
		margin: 0;
	}

	.batch input {
		flex: 1;
		min-width: 200px;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
	}
</style>
