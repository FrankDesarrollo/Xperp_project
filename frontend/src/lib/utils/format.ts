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
export function formatCop(amount: string | number): string {
	const value = typeof amount === 'string' ? Number(amount) : amount;
	if (!Number.isFinite(value)) return '—';
	const sign = value < 0 ? '-' : '';
	return `${sign}$ ${copFormatter.format(Math.abs(value))}`;
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

/** Formatea una fecha ISO 8601 como `dd/mm/aaaa hh:mm` en hora de Bogotá. */
export function formatDateTime(iso: string | Date): string {
	const date = typeof iso === 'string' ? new Date(iso) : iso;
	if (Number.isNaN(date.getTime())) return '—';
	const parts = Object.fromEntries(dateTimeParts.formatToParts(date).map((p) => [p.type, p.value]));
	return `${parts.day}/${parts.month}/${parts.year} ${parts.hour}:${parts.minute}`;
}
