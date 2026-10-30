/**
 * Opciones comunes de ECharts: marcas delgadas, ejes y rejilla discretos, tooltip con crosshair y
 * texto en tinta de texto (nunca en el color de la serie). Ver la guía de visualización del repo.
 */
import type { EChartsOption } from 'echarts';
import type { ChartTheme } from './EChart.svelte';
import { formatCopShort, formatDate, formatNumber } from '$lib/utils/format';

export interface LineSeries {
	name: string;
	points: { date: string; value: number }[];
	dashed?: boolean;
	area?: boolean;
}

/** Serie temporal de una o pocas series en un solo eje. */
export function lineOption(
	t: ChartTheme,
	series: LineSeries[],
	valueKind: 'money' | 'units' | 'ratio' = 'units'
): EChartsOption {
	const fmt = (v: number) =>
		valueKind === 'money'
			? formatCopShort(v)
			: valueKind === 'ratio'
				? `${formatNumber(v * 100, 1)} %`
				: formatNumber(v);
	const dates = Array.from(new Set(series.flatMap((s) => s.points.map((p) => p.date)))).sort();
	return {
		animation: false,
		grid: { left: 8, right: 16, top: series.length > 1 ? 32 : 12, bottom: 8, containLabel: true },
		legend:
			series.length > 1
				? {
						top: 0,
						left: 0,
						textStyle: { color: t.muted },
						icon: 'roundRect',
						itemWidth: 14,
						itemHeight: 4
					}
				: undefined,
		tooltip: {
			trigger: 'axis',
			axisPointer: { type: 'line', lineStyle: { color: t.border } },
			backgroundColor: t.surface,
			borderColor: t.border,
			textStyle: { color: t.text },
			valueFormatter: (v) => (typeof v === 'number' ? fmt(v) : String(v ?? '—'))
		},
		xAxis: {
			type: 'category',
			data: dates,
			boundaryGap: false,
			axisLine: { lineStyle: { color: t.border } },
			axisTick: { show: false },
			axisLabel: { color: t.muted, formatter: (v: string) => formatDate(v).slice(0, 5) }
		},
		yAxis: {
			type: 'value',
			splitLine: { lineStyle: { color: t.border, opacity: 0.6 } },
			axisLabel: { color: t.muted, formatter: (v: number) => fmt(v) }
		},
		series: series.map((s, i) => {
			const map = new Map(s.points.map((p) => [p.date, p.value]));
			const color = t.series[i % t.series.length];
			return {
				name: s.name,
				type: 'line',
				data: dates.map((d) => map.get(d) ?? null),
				showSymbol: false,
				symbolSize: 8,
				lineStyle: { width: 2, type: s.dashed ? 'dashed' : 'solid', color },
				itemStyle: { color },
				areaStyle: s.area ? { color, opacity: 0.08 } : undefined,
				connectNulls: false
			};
		})
	};
}

export interface BarDatum {
	label: string;
	value: number;
	/** Color de estado opcional (por ejemplo, presupuesto pasado del 85 %). */
	tone?: 'danger' | 'warning' | 'success';
}

/** Barras horizontales de una sola serie con etiqueta directa del valor. */
export function barOption(
	t: ChartTheme,
	data: BarDatum[],
	format: (v: number) => string,
	max?: number
): EChartsOption {
	return {
		animation: false,
		grid: { left: 8, right: 56, top: 8, bottom: 8, containLabel: true },
		tooltip: {
			trigger: 'item',
			backgroundColor: t.surface,
			borderColor: t.border,
			textStyle: { color: t.text },
			formatter: (p) => {
				const item = Array.isArray(p) ? p[0] : p;
				return `${item?.name ?? ''}: ${format(Number(item?.value ?? 0))}`;
			}
		},
		xAxis: {
			type: 'value',
			max,
			splitLine: { lineStyle: { color: t.border, opacity: 0.6 } },
			axisLabel: { color: t.muted, formatter: (v: number) => format(v) }
		},
		yAxis: {
			type: 'category',
			data: data.map((d) => d.label),
			inverse: true,
			axisTick: { show: false },
			axisLine: { lineStyle: { color: t.border } },
			axisLabel: { color: t.text }
		},
		series: [
			{
				type: 'bar',
				barMaxWidth: 18,
				data: data.map((d) => ({
					value: d.value,
					itemStyle: {
						color: d.tone ? t[d.tone] : t.series[0],
						borderRadius: [0, 4, 4, 0]
					}
				})),
				label: {
					show: true,
					position: 'right',
					color: t.muted,
					formatter: (p) => format(Number(p.value))
				}
			}
		]
	};
}
