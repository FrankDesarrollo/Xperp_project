<script lang="ts">
	/** Lista clave-valor de un objeto plano; los valores anidados se muestran como tabla. */
	import DataTable from './DataTable.svelte';

	interface Props {
		data: Record<string, unknown>;
	}

	let { data }: Props = $props();

	let flat = $derived(
		Object.entries(data).filter(
			([, v]) => !Array.isArray(v) && (v === null || typeof v !== 'object')
		)
	);
	let nested = $derived(
		Object.entries(data).filter(
			([, v]) => Array.isArray(v) || (v !== null && typeof v === 'object')
		)
	);

	function show(v: unknown): string {
		if (v === null || v === undefined) return '—';
		if (typeof v === 'boolean') return v ? 'sí' : 'no';
		if (typeof v === 'number')
			return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 3 }).format(v);
		return String(v);
	}

	function rows(v: unknown): Record<string, unknown>[] {
		if (Array.isArray(v)) {
			return v.map((x) =>
				x !== null && typeof x === 'object' ? (x as Record<string, unknown>) : { valor: x }
			);
		}
		return Object.entries(v as Record<string, unknown>).map(([k, val]) => ({
			clave: k,
			valor: val
		}));
	}
</script>

{#if flat.length > 0}
	<dl class="facts">
		{#each flat as [k, v] (k)}
			<dt>{k.replaceAll('_', ' ')}</dt>
			<dd>{show(v)}</dd>
		{/each}
	</dl>
{/if}
{#each nested as [k, v] (k)}
	<div class="nested">
		<p class="small text-muted">{k.replaceAll('_', ' ')}</p>
		<DataTable rows={rows(v)} />
	</div>
{/each}

<style>
	.nested {
		margin-top: var(--space-3);
		display: grid;
		gap: var(--space-2);
	}

	.nested p {
		margin: 0;
	}
</style>
