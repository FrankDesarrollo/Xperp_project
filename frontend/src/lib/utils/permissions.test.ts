import { describe, expect, it } from 'vitest';
import { canAccess, sectionFor } from './permissions';

describe('sectionFor', () => {
	it('reconoce la sección de una ruta, con o sin subruta', () => {
		expect(sectionFor('/inventario')).toBe('inventario');
		expect(sectionFor('/ia/decisiones')).toBe('ia/decisiones');
		expect(sectionFor('/ia/politicas/')).toBe('ia/politicas');
		expect(sectionFor('/compras/12')).toBe('compras');
	});

	it('no confunde secciones que empiezan igual', () => {
		expect(sectionFor('/ventasx')).toBeNull();
	});

	it('una ruta que no es de ninguna sección no tiene permisos que revisar', () => {
		expect(sectionFor('/')).toBeNull();
		expect(sectionFor('/login')).toBeNull();
	});
});

describe('canAccess', () => {
	it('el bodeguero no entra a políticas ni a compras, el administrador sí', () => {
		expect(canAccess('warehouse', 'ia/politicas')).toBe(false);
		expect(canAccess('warehouse', 'compras')).toBe(false);
		expect(canAccess('admin', 'ia/politicas')).toBe(true);
	});

	it('sin rol no se entra a ninguna sección', () => {
		expect(canAccess(undefined, 'tablero')).toBe(false);
	});
});
