<script lang="ts" module>
	/** Colores de los tokens resueltos en el tema actual, para pasarlos a ECharts. */
	export interface ChartTheme {
		series: string[];
		text: string;
		muted: string;
		border: string;
		surface: string;
		danger: string;
		warning: string;
		success: string;
	}
</script>

<script lang="ts">
	import { onMount } from 'svelte';
	import type { EChartsOption, ECharts } from 'echarts';

	interface Props {
		/** Construye la opción con los colores del tema vigente. */
		option: (theme: ChartTheme) => EChartsOption;
		height?: string;
		label: string;
	}

	let { option, height = '260px', label }: Props = $props();

	let el: HTMLDivElement;
	let chart: ECharts | null = null;
	let themeVersion = $state(0);

	function readTheme(): ChartTheme {
		const css = getComputedStyle(document.documentElement);
		const v = (name: string) => css.getPropertyValue(name).trim();
		return {
			series: [v('--chart-1'), v('--chart-2'), v('--chart-3'), v('--chart-4')],
			text: v('--color-text'),
			muted: v('--color-text-muted'),
			border: v('--color-border'),
			surface: v('--color-surface'),
			danger: v('--color-danger'),
			warning: v('--color-warning'),
			success: v('--color-success')
		};
	}

	onMount(() => {
		let disposed = false;
		const observer = new ResizeObserver(() => chart?.resize());
		const media = window.matchMedia('(prefers-color-scheme: dark)');
		const bump = () => themeVersion++;
		const attr = new MutationObserver(bump);
		void import('echarts').then((echarts) => {
			if (disposed) return;
			chart = echarts.init(el, undefined, { renderer: 'svg' });
			observer.observe(el);
			themeVersion++;
		});
		media.addEventListener('change', bump);
		attr.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
		return () => {
			disposed = true;
			observer.disconnect();
			attr.disconnect();
			media.removeEventListener('change', bump);
			chart?.dispose();
			chart = null;
		};
	});

	$effect(() => {
		void themeVersion;
		const build = option;
		if (chart) {
			chart.setOption(build(readTheme()), { notMerge: true });
		}
	});
</script>

<div bind:this={el} class="chart" style:height role="img" aria-label={label}></div>

<style>
	.chart {
		width: 100%;
	}
</style>
