<script lang="ts">
	import { Check, X } from '@lucide/svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import DecisionCard from './DecisionCard.svelte';
	import type { Approval } from '$lib/api/types';
	import { formatDateTime } from '$lib/utils/format';
	import { ROLE_LABEL } from '$lib/utils/labels';

	interface Props {
		approval: Approval;
		canDecide: boolean;
		busy?: boolean;
		/** Si se puede marcar para aprobar en lote. */
		selected?: boolean;
		onselect?: (a: Approval, selected: boolean) => void;
		onapprove: (a: Approval, comment: string) => void;
		onreject: (a: Approval, comment: string) => void;
	}

	let {
		approval: a,
		canDecide,
		busy = false,
		selected = false,
		onselect,
		onapprove,
		onreject
	}: Props = $props();
	let comment = $state('');
	let expanded = $state(false);
</script>

<div class="approval" class:selected>
	<div class="head row">
		{#if onselect && canDecide && a.status === 'pending'}
			<label class="pick">
				<input
					type="checkbox"
					checked={selected}
					onchange={(e) => onselect(a, e.currentTarget.checked)}
				/>
				<span class="visually-hidden">Marcar para aprobar en lote: {a.decision.title}</span>
			</label>
		{/if}
		<Badge tone="warning">Aprueba {ROLE_LABEL[a.requested_role] ?? a.requested_role}</Badge>
		<span class="text-muted small"
			>Pedida {formatDateTime(a.created_at)} · vence {formatDateTime(a.expires_at)}</span
		>
		<span class="spacer"></span>
		<button class="link small" onclick={() => (expanded = !expanded)}>
			{expanded ? 'Ocultar detalle' : 'Ver qué vio la IA'}
		</button>
	</div>
	<DecisionCard decision={a.decision} compact={!expanded} />
	{#if a.status === 'pending'}
		{#if canDecide}
			<div class="actions">
				<input
					placeholder="Comentario (opcional)"
					bind:value={comment}
					aria-label="Comentario de la aprobación"
				/>
				<Button variant="danger" loading={busy} onclick={() => onreject(a, comment)}
					><X size={16} />Rechazar</Button
				>
				<Button variant="primary" loading={busy} onclick={() => onapprove(a, comment)}
					><Check size={16} />Aprobar</Button
				>
			</div>
		{:else}
			<p class="text-muted small">
				Esta aprobación le corresponde a {ROLE_LABEL[a.requested_role]}.
			</p>
		{/if}
	{:else}
		<p class="small">
			<strong
				>{a.status === 'approved'
					? 'Aprobada'
					: a.status === 'rejected'
						? 'Rechazada'
						: 'Vencida'}</strong
			>
			{#if a.decided_by_name}por {a.decided_by_name}{/if}
			{#if a.decided_at}· {formatDateTime(a.decided_at)}{/if}
			{#if a.comment}· «{a.comment}»{/if}
		</p>
	{/if}
</div>

<style>
	.approval {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
		background: var(--color-surface-muted);
		border-radius: var(--radius-lg);
		border: 1px solid var(--color-border);
	}

	.selected {
		border-color: var(--color-primary);
	}

	.pick {
		display: inline-flex;
		align-items: center;
		min-height: 2.75rem;
		min-width: 2.75rem;
		justify-content: center;
		cursor: pointer;
	}

	.actions {
		display: flex;
		gap: var(--space-3);
		flex-wrap: wrap;
	}

	.actions input {
		flex: 1;
		min-width: 200px;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
	}

	.link {
		border: none;
		background: none;
		color: var(--color-primary);
		cursor: pointer;
		padding: 0;
	}

	p {
		margin: 0;
	}
</style>
