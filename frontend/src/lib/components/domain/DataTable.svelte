<script lang="ts">
	/** Tabla simple para objetos clave-valor (datos de entrada y alternativas de la IA). */
	interface Props {
		rows: Record<string, unknown>[];
		max?: number;
	}

	let { rows, max = 20 }: Props = $props();

	let columns = $derived(Array.from(new Set(rows.flatMap((r) => Object.keys(r)))));

	function show(v: unknown): string {
		if (v === null || v === undefined) return '—';
		if (typeof v === 'boolean') return v ? 'sí' : 'no';
		if (typeof v === 'number')
			return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 3 }).format(v);
		if (typeof v === 'object') return JSON.stringify(v);
		return String(v);
	}

	function header(c: string): string {
		return c.replaceAll('_', ' ');
	}
</script>

{#if rows.length > 0}
	<div class="table-wrap">
		<table class="data">
			<thead>
				<tr>
					{#each columns as c (c)}<th>{header(c)}</th>{/each}
				</tr>
			</thead>
			<tbody>
				{#each rows.slice(0, max) as r, i (i)}
					<tr>
						{#each columns as c (c)}
							<td class:num={typeof r[c] === 'number'}>{show(r[c])}</td>
						{/each}
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
	{#if rows.length > max}<p class="text-muted small">…y {rows.length - max} filas más.</p>{/if}
{/if}
