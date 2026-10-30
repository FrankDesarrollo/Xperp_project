<script lang="ts">
	import { formatNumber } from '$lib/utils/format';

	interface Props {
		page: number;
		size: number;
		total: number;
		onchange: (page: number) => void;
	}

	let { page, size, total, onchange }: Props = $props();
	let pages = $derived(Math.max(1, Math.ceil(total / size)));
</script>

<nav class="pagination" aria-label="Paginación">
	<span class="text-muted small">{formatNumber(total)} registros · página {page} de {pages}</span>
	<div class="row">
		<button disabled={page <= 1} onclick={() => onchange(page - 1)}>Anterior</button>
		<button disabled={page >= pages} onclick={() => onchange(page + 1)}>Siguiente</button>
	</div>
</nav>

<style>
	.pagination {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) 0;
		flex-wrap: wrap;
	}

	button {
		border: 1px solid var(--color-border);
		background: var(--color-surface);
		border-radius: var(--radius-md);
		padding: var(--space-1) var(--space-3);
		cursor: pointer;
		font-size: var(--text-sm);
	}

	button:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}
</style>
