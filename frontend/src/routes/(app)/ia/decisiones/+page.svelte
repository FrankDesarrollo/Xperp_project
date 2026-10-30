<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { AiDecision } from '$lib/api/types';
	import { auth } from '$lib/stores/auth.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import DecisionCard from '$lib/components/domain/DecisionCard.svelte';
	import Drawer from '$lib/components/ui/Drawer.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import EmptyState from '$lib/components/ui/EmptyState.svelte';
	import { DECISION_STATUS, DECISION_TYPE } from '$lib/utils/labels';

	const SIZE = 20;
	let items = $state<AiDecision[]>([]);
	let total = $state(0);
	let pageNo = $state(1);
	let type = $state('');
	let status = $state('');
	let hideRoutine = $state(true);
	let selected = $state<AiDecision | null>(null);
	let pending = $state(0);

	async function load() {
		const res = await unwrap(
			api.GET('/ai/decisions', {
				params: {
					query: {
						decision_type: type || undefined,
						status: status || undefined,
						exclude_type: hideRoutine && !type ? 'fulfillment' : undefined,
						page: pageNo,
						size: SIZE
					}
				}
			})
		);
		items = res.items;
		total = res.total;
		pending = 0;
	}

	async function openId(id: number) {
		selected = await unwrap(api.GET('/ai/decisions/{id}', { params: { path: { id } } }));
	}

	async function revert(d: AiDecision) {
		try {
			selected = await unwrap(
				api.POST('/ai/decisions/{id}/revert', {
					params: { path: { id: d.id } },
					body: { reason: `Revertida por ${auth.user?.full_name}` }
				})
			);
			toasts.success('Decisión revertida', selected.result_message ?? undefined);
			await load();
		} catch (e) {
			toasts.error('No se pudo revertir', e instanceof ApiError ? e.message : undefined);
		}
	}

	onMount(() => {
		void load();
		const id = Number(page.url.searchParams.get('id'));
		if (id) void openId(id);
		const off = live.on('ai.decision', () => {
			if (pageNo === 1) pending++;
		});
		return off;
	});

	function apply() {
		pageNo = 1;
		void load();
	}
</script>

<svelte:head><title>Decisiones de la IA · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Decisiones de la IA</h1>
		<p>Bitácora completa: qué vio, qué alternativas tenía, qué eligió, por qué y quién la aprobó</p>
	</div>
</div>

<div class="filters">
	<Field label="Tipo">
		<select bind:value={type} onchange={apply}>
			<option value="">Todos</option>
			{#each Object.entries(DECISION_TYPE) as [k, v] (k)}{#if k !== 'assistant_answer'}<option
						value={k}>{v}</option
					>{/if}{/each}
		</select>
	</Field>
	<Field label="Estado">
		<select bind:value={status} onchange={apply}>
			<option value="">Todos</option>
			{#each Object.entries(DECISION_STATUS) as [k, v] (k)}<option value={k}>{v.label}</option
				>{/each}
		</select>
	</Field>
	<label class="row small check"
		><input type="checkbox" bind:checked={hideRoutine} onchange={apply} /> Ocultar alistamientos rutinarios</label
	>
	{#if pending > 0}
		<button class="new" onclick={load}>{pending} decisiones nuevas · actualizar</button>
	{/if}
</div>

{#if items.length === 0}
	<EmptyState title="No hay decisiones con estos filtros"
		>Inicia la simulación para que la IA empiece a operar.</EmptyState
	>
{:else}
	<div class="list">
		{#each items as d (d.id)}
			<DecisionCard decision={d} compact onopen={(x) => openId(x.id)} />
		{/each}
	</div>
	<Pagination page={pageNo} size={SIZE} {total} onchange={(p) => ((pageNo = p), load())} />
{/if}

<Drawer
	open={selected !== null}
	wide
	title={selected ? `Decisión #${selected.id}` : ''}
	subtitle={selected ? DECISION_TYPE[selected.decision_type] : ''}
	onclose={() => (selected = null)}
>
	{#if selected}
		<DecisionCard
			decision={selected}
			canRevert={auth.hasRole('admin', 'manager')}
			onrevert={revert}
		/>
	{/if}
</Drawer>

<style>
	.list {
		display: grid;
		gap: var(--space-3);
	}

	.check {
		align-self: center;
	}

	.new {
		align-self: center;
		border: 1px solid var(--color-ai);
		color: var(--color-ai);
		background: var(--color-ai-bg);
		border-radius: var(--radius-full);
		padding: var(--space-1) var(--space-3);
		cursor: pointer;
	}
</style>
