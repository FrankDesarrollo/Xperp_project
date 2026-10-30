<script lang="ts">
	import { formatDateTime } from '$lib/utils/format';

	interface Item {
		title: string;
		note?: string | null;
		at: string;
		tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger';
	}

	interface Props {
		items: Item[];
	}

	let { items }: Props = $props();
</script>

<ol class="timeline">
	{#each items as item, i (i)}
		<li class={item.tone ?? 'neutral'}>
			<span class="dot" aria-hidden="true"></span>
			<div>
				<strong>{item.title}</strong>
				<span class="text-muted small">{formatDateTime(item.at)}</span>
				{#if item.note}<p class="small">{item.note}</p>{/if}
			</div>
		</li>
	{/each}
</ol>

<style>
	.timeline {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-3);
	}

	li {
		display: grid;
		grid-template-columns: 14px 1fr;
		gap: var(--space-3);
		position: relative;
	}

	li:not(:last-child)::before {
		content: '';
		position: absolute;
		left: 6px;
		top: 16px;
		bottom: calc(-1 * var(--space-3));
		width: 2px;
		background: var(--color-border);
	}

	.dot {
		width: 14px;
		height: 14px;
		border-radius: var(--radius-full);
		background: var(--color-text-muted);
		margin-top: 3px;
		border: 2px solid var(--color-surface);
	}

	li div {
		display: grid;
		gap: 2px;
	}

	p {
		margin: 0;
	}

	.info .dot {
		background: var(--color-info);
	}

	.success .dot {
		background: var(--color-success);
	}

	.warning .dot {
		background: var(--color-warning);
	}

	.danger .dot {
		background: var(--color-danger);
	}
</style>
