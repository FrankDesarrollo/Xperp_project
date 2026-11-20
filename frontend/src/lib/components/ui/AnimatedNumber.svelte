<script lang="ts">
	import { Tween } from 'svelte/motion';
	import { cubicOut } from 'svelte/easing';

	/**
	 * Número que cuenta hasta su valor al aparecer y se desliza al cambiar. El lector de pantalla
	 * lee solo el valor final, no cada paso de la animación.
	 */
	interface Props {
		value: number;
		format: (n: number) => string;
		duration?: number;
	}

	let { value, format, duration = 1100 }: Props = $props();

	const reduce =
		typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
	const tween = new Tween(0, { easing: cubicOut });

	$effect(() => {
		void tween.set(Number.isFinite(value) ? value : 0, { duration: reduce ? 0 : duration });
	});
</script>

<span class="num" aria-hidden="true">{format(tween.current)}</span><span class="visually-hidden"
	>{format(value)}</span
>

<style>
	.num {
		font-variant-numeric: tabular-nums;
	}
</style>
