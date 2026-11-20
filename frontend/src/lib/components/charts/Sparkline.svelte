<script lang="ts">
	/**
	 * Mini gráfica de tendencia, sin ejes, para acompañar una métrica. La línea se dibuja al
	 * aparecer. Es decorativa: el valor y su contexto van en texto al lado.
	 */
	interface Props {
		values: number[];
		/** Color de la línea: un token CSS, por ejemplo var(--color-primary). */
		color?: string;
		height?: number;
		/** Fija el mínimo del eje (p. ej. 0) para no exagerar variaciones pequeñas. */
		min?: number;
		max?: number;
	}

	let { values, color = 'var(--color-primary)', height = 44, min, max }: Props = $props();

	const uid = $props.id();
	const W = 100;
	const H = 30;

	let path = $derived.by(() => {
		const v = values.filter((x) => Number.isFinite(x));
		if (v.length < 2) return { line: '', area: '' };
		const lo = min ?? Math.min(...v);
		const hi = max ?? Math.max(...v);
		const span = hi - lo || 1;
		const pts: [number, number][] = v.map((x, i) => [
			(i / (v.length - 1)) * W,
			H - 2 - ((x - lo) / span) * (H - 4)
		]);
		const line = pts
			.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`)
			.join(' ');
		return { line, area: `${line} L${W},${H} L0,${H} Z` };
	});
</script>

{#if path.line}
	<svg
		viewBox="0 0 {W} {H}"
		preserveAspectRatio="none"
		style:height="{height}px"
		style:--spark={color}
		aria-hidden="true"
	>
		<defs>
			<linearGradient id="spark-{uid}" x1="0" y1="0" x2="0" y2="1">
				<stop offset="0%" style:stop-color={color} stop-opacity="0.14" />
				<stop offset="100%" style:stop-color={color} stop-opacity="0" />
			</linearGradient>
		</defs>
		<path class="area" d={path.area} fill="url(#spark-{uid})" />
		<path class="line" d={path.line} />
	</svg>
{/if}

<style>
	svg {
		clip-path: inset(0 100% 0 0);
		animation: draw 1.4s var(--ease-out) 0.2s forwards;
		display: block;
		width: 100%;
		overflow: visible;
	}

	.line {
		fill: none;
		stroke: var(--spark);
		stroke-width: 1.75;
		stroke-linecap: round;
		stroke-linejoin: round;
		vector-effect: non-scaling-stroke;
	}

	.area {
		opacity: 0;
		animation: fade var(--duration-slow) var(--ease-out) 0.9s forwards;
	}

	@keyframes draw {
		to {
			clip-path: inset(0 0 0 0);
		}
	}

	@keyframes fade {
		to {
			opacity: 1;
		}
	}
</style>
