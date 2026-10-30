<script lang="ts">
	import { Play, CheckCheck } from '@lucide/svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import StatusBadge from './StatusBadge.svelte';
	import type { WarehouseTask } from '$lib/api/types';
	import { formatDateTime, formatNumber } from '$lib/utils/format';
	import { TASK_STATUS, TASK_TYPE } from '$lib/utils/labels';

	/** Tarea de bodega con botones grandes, pensada para celular (AGENTS.md, 11.1). */
	interface Props {
		task: WarehouseTask;
		busy?: boolean;
		onstart?: (t: WarehouseTask) => void;
		oncomplete?: (t: WarehouseTask) => void;
	}

	let { task: t, busy = false, onstart, oncomplete }: Props = $props();
</script>

<article class="task priority-{t.priority}">
	<header>
		<span class="type">{TASK_TYPE[t.type] ?? t.type}</span>
		<StatusBadge map={TASK_STATUS} value={t.status} />
	</header>
	<h3>{t.title}</h3>
	{#if t.detail}<p class="text-muted small">{t.detail}</p>{/if}
	<p class="small">
		{t.warehouse_code} · vence {formatDateTime(t.due_at)}
		{#if t.assigned_to_name}· {t.assigned_to_name}{/if}
	</p>
	{#if t.lines.length > 0}
		<ul>
			{#each t.lines.slice(0, 8) as l (l.product_id)}
				<li>
					<span class="mono">{l.sku}</span>
					{l.product_name} <strong>× {formatNumber(l.quantity)}</strong>
				</li>
			{/each}
			{#if t.lines.length > 8}<li class="text-muted">…y {t.lines.length - 8} más</li>{/if}
		</ul>
	{/if}
	{#if t.status === 'pending' && onstart}
		<Button size="lg" variant="secondary" loading={busy} onclick={() => onstart?.(t)}
			><Play size={20} />Iniciar</Button
		>
	{/if}
	{#if (t.status === 'pending' || t.status === 'in_progress') && oncomplete}
		<Button size="lg" variant="primary" loading={busy} onclick={() => oncomplete?.(t)}>
			<CheckCheck size={20} />Terminar
		</Button>
	{/if}
	{#if t.status === 'done'}
		<p class="small text-muted">
			Terminada {formatDateTime(t.completed_at)}
			{t.completed_by_name ? `por ${t.completed_by_name}` : ''}
		</p>
	{/if}
</article>

<style>
	.task {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-4);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		border-left: 4px solid var(--color-border);
	}

	.priority-1 {
		border-left-color: var(--color-danger);
	}

	.priority-2 {
		border-left-color: var(--color-warning);
	}

	header {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.type {
		font-size: var(--text-xs);
		text-transform: uppercase;
		letter-spacing: 0.05em;
		color: var(--color-text-muted);
		font-weight: var(--weight-bold);
	}

	h3 {
		margin: 0;
		font-size: var(--text-lg);
	}

	p {
		margin: 0;
	}

	ul {
		margin: 0;
		padding-left: var(--space-5);
		font-size: var(--text-sm);
		display: grid;
		gap: 2px;
	}
</style>
