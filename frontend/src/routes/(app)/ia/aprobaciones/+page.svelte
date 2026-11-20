<script lang="ts">
	import { onMount } from 'svelte';
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
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import EmptyState from '$lib/components/ui/EmptyState.svelte';

	const SIZE = 10;
	const DECIDERS: Record<string, string[]> = {
		purchasing: ['purchasing', 'manager', 'admin'],
		manager: ['manager', 'admin'],
		admin: ['admin']
	};

	let tab = $state('pending');
	let items = $state<Approval[]>([]);
	let total = $state(0);
	let pageNo = $state(1);
	let busyId = $state<number | null>(null);

	const loader = new Loader();

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
						page: pageNo,
						size: SIZE
					}
				}
			})
		);
		items = res.items;
		total = res.total;
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

	function canDecide(a: Approval): boolean {
		return (DECIDERS[a.requested_role] ?? ['admin']).includes(auth.user?.role ?? '');
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
		<p>Lo que la IA preparó y necesita tu visto bueno. Vence a las 24 horas simuladas.</p>
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
	onchange={(id) => ((tab = id), (pageNo = 1), load())}
/>

<LoadState {loader} what="las aprobaciones" onretry={load}>
	{#if items.length === 0}
		<EmptyState
			title={tab === 'pending'
				? 'No tienes aprobaciones pendientes'
				: 'No hay aprobaciones en esta lista'}
		>
			Toda compra, reasignación, cancelación, traslado o ajuste de inventario que recomiende la IA
			llega aquí con su impacto. Si nadie la decide en 24 horas simuladas, vence.
		</EmptyState>
	{:else}
		<div class="list">
			{#each items as a (a.id)}
				<ApprovalCard
					approval={a}
					canDecide={canDecide(a)}
					busy={busyId === a.id}
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
</style>
