/**
 * Formatos de la interfaz (AGENTS.md, sección 11.2):
 * montos como `$ 1.250.000` y fechas como `dd/mm/aaaa hh:mm`, siempre en America/Bogota.
 */

export const TIME_ZONE = 'America/Bogota';

const copFormatter = new Intl.NumberFormat('es-CO', {
	maximumFractionDigits: 0,
	minimumFractionDigits: 0
});

/** Formatea un monto en COP. Acepta el string decimal de la API (`"125000.00"`). */
export function formatCop(amount: string | number | null | undefined): string {
	if (amount === null || amount === undefined) return '—';
	const value = typeof amount === 'string' ? Number(amount) : amount;
	if (!Number.isFinite(value)) return '—';
	const sign = value < 0 ? '-' : '';
	return `${sign}$ ${copFormatter.format(Math.abs(value))}`;
}

/** Monto abreviado para tarjetas: $ 12,5 M, $ 830 mil. */
export function formatCopShort(amount: string | number | null | undefined): string {
	if (amount === null || amount === undefined) return '—';
	const value = typeof amount === 'string' ? Number(amount) : amount;
	if (!Number.isFinite(value)) return '—';
	const abs = Math.abs(value);
	const sign = value < 0 ? '-' : '';
	if (abs >= 1_000_000_000) return `${sign}$ ${formatNumber(abs / 1_000_000_000, 1)} mil M`;
	if (abs >= 1_000_000) return `${sign}$ ${formatNumber(abs / 1_000_000, 1)} M`;
	if (abs >= 1_000) return `${sign}$ ${formatNumber(abs / 1_000, 0)} mil`;
	return formatCop(value);
}

const dateTimeParts = new Intl.DateTimeFormat('es-CO', {
	timeZone: TIME_ZONE,
	day: '2-digit',
	month: '2-digit',
	year: 'numeric',
	hour: '2-digit',
	minute: '2-digit',
	hourCycle: 'h23'
});

function parts(iso: string | Date): Record<string, string> | null {
	const date = typeof iso === 'string' ? new Date(iso) : iso;
	if (Number.isNaN(date.getTime())) return null;
	return Object.fromEntries(dateTimeParts.formatToParts(date).map((p) => [p.type, p.value]));
}

/** Formatea una fecha ISO 8601 como `dd/mm/aaaa hh:mm` en hora de Bogotá. */
export function formatDateTime(iso: string | Date | null | undefined): string {
	if (!iso) return '—';
	const p = parts(iso);
	if (!p) return '—';
	return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}`;
}

/** `dd/mm/aaaa`. Acepta fechas `aaaa-mm-dd` sin hora. */
export function formatDate(iso: string | Date | null | undefined): string {
	if (!iso) return '—';
	if (typeof iso === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(iso)) {
		const [y, m, d] = iso.split('-');
		return `${d}/${m}/${y}`;
	}
	const p = parts(iso);
	return p ? `${p.day}/${p.month}/${p.year}` : '—';
}

/** `hh:mm` en Bogotá. */
export function formatTime(iso: string | Date | null | undefined): string {
	if (!iso) return '—';
	const p = parts(iso);
	return p ? `${p.hour}:${p.minute}` : '—';
}

export function formatNumber(value: number | null | undefined, decimals = 0): string {
	if (value === null || value === undefined || !Number.isFinite(value)) return '—';
	return new Intl.NumberFormat('es-CO', {
		minimumFractionDigits: decimals,
		maximumFractionDigits: decimals
	}).format(value);
}

/** Porcentaje: 0,9842 → «98,4 %» si `ratio`; 76,28 → «76,3 %» si no. */
export function formatPct(value: number | null | undefined, ratio = false, decimals = 1): string {
	if (value === null || value === undefined || !Number.isFinite(value)) return '—';
	return `${formatNumber(ratio ? value * 100 : value, decimals)} %`;
}
