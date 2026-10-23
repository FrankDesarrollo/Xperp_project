import { describe, expect, it } from 'vitest';
import { formatCop, formatDateTime } from './format';

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
