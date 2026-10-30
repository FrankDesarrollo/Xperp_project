import type { components } from '$lib/api/schema';

export type Role = components['schemas']['Role'];

/** Roles que pueden ver cada sección de la interfaz (AGENTS.md, secciones 9.4 y 11.1). */
export const SECTION_ROLES = {
	tablero: ['admin', 'manager', 'purchasing', 'warehouse', 'viewer'],
	inventario: ['admin', 'manager', 'purchasing', 'warehouse', 'viewer'],
	ventas: ['admin', 'manager', 'viewer'],
	compras: ['admin', 'manager', 'purchasing'],
	bodega: ['admin', 'manager', 'warehouse'],
	despachos: ['admin', 'manager', 'warehouse'],
	'ia/decisiones': ['admin', 'manager', 'purchasing', 'warehouse', 'viewer'],
	'ia/aprobaciones': ['admin', 'manager', 'purchasing'],
	'ia/politicas': ['admin'],
	asistente: ['admin', 'manager', 'purchasing', 'warehouse', 'viewer'],
	simulacion: ['admin'],
	configuracion: ['admin']
} as const satisfies Record<string, readonly Role[]>;

export type Section = keyof typeof SECTION_ROLES;

export function canAccess(role: Role | undefined, section: Section): boolean {
	return role !== undefined && (SECTION_ROLES[section] as readonly Role[]).includes(role);
}
