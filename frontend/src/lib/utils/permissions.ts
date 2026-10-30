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

/**
 * Sección de la interfaz a la que pertenece una ruta (`/ia/decisiones/5` → `ia/decisiones`), o
 * `null` si la ruta no es de ninguna sección con permisos.
 */
export function sectionFor(pathname: string): Section | null {
	const path = pathname.replace(/^\/+|\/+$/g, '');
	let best: Section | null = null;
	for (const section of Object.keys(SECTION_ROLES) as Section[]) {
		if (
			(path === section || path.startsWith(section + '/')) &&
			section.length > (best?.length ?? 0)
		) {
			best = section;
		}
	}
	return best;
}
