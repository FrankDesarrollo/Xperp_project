import { describe, expect, it } from 'vitest';
import {
	formatCop,
	formatCopShort,
	formatDate,
	formatDateTime,
	formatImpact,
	formatPct,
	formatTime
} from './format';

describe('formatCop', () => {
	it('formatea montos con separador de miles y sin decimales', () => {
		expect(formatCop('1250000.00')).toBe('$ 1.250.000');
		expect(formatCop(4500)).toBe('$ 4.500');
	});

	it('conserva el signo negativo', () => {
		expect(formatCop('-18000.00')).toBe('-$ 18.000');
	});

	it('devuelve un guion si el valor no es numérico', () => {
		expect(formatCop('abc')).toBe('—');
	});
});

describe('formatDateTime', () => {
	it('usa dd/mm/aaaa hh:mm en hora de Bogotá', () => {
		expect(formatDateTime('2026-11-04T15:15:00Z')).toBe('04/11/2026 10:15');
	});

	it('devuelve un guion si la fecha no es válida', () => {
		expect(formatDateTime('no-es-fecha')).toBe('—');
	});
});

describe('formatos adicionales', () => {
	it('abrevia montos grandes', () => {
		expect(formatCopShort('12500000.00')).toBe('$ 12,5 M');
		expect(formatCopShort(830000)).toBe('$ 830 mil');
	});

	it('formatea porcentajes desde razones o valores', () => {
		expect(formatPct(0.9842, true)).toBe('98,4 %');
		expect(formatPct(76.28)).toBe('76,3 %');
	});

	it('formatea fechas sin hora y horas', () => {
		expect(formatDate('2026-11-15')).toBe('15/11/2026');
		expect(formatTime('2026-11-04T15:15:00Z')).toBe('10:15');
	});
});

describe('formatImpact', () => {
	it('muestra cada cifra del impacto según su unidad', () => {
		expect(formatImpact('1872000.00', 'cop')).toBe('$ 1.872.000');
		expect(formatImpact('10.0', 'pct')).toBe('10,0 %');
		expect(formatImpact('0.5', 'days')).toBe('0,5 días');
		expect(formatImpact('1', 'days')).toBe('1,0 día');
		expect(formatImpact('300', 'units')).toBe('300 und');
		expect(formatImpact('abc', 'otra')).toBe('abc');
	});
});
