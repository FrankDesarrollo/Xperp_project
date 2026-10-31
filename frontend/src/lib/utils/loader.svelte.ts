import { ApiError } from '$lib/api/client';

/** Una petición cancelada (el usuario cambió de pantalla) no es un error que mostrar. */
export function isAbort(e: unknown): boolean {
	return e instanceof DOMException && e.name === 'AbortError';
}

/** Explica el fallo con palabras del usuario y le dice qué puede hacer. */
export function describeLoadError(e: unknown): string {
	if (e instanceof ApiError) {
		if (e.status === 403) return 'Tu rol no tiene permiso para ver esta información.';
		if (e.status >= 500) return 'El servidor tuvo un problema al responder. Intenta de nuevo.';
		return e.message;
	}
	return 'No se pudo conectar con el servidor. Revisa la conexión e intenta de nuevo.';
}

/**
 * Estado de la carga de una vista: cargando, con error o con datos.
 *
 * La primera carga muestra «Cargando…» y, si falla, un error con salida. Las recargas
 * posteriores (por SSE o por filtros) no esconden lo que ya se ve: si fallan, el error
 * aparece encima de los datos anteriores.
 */
export class Loader {
	loading = $state(true);
	loaded = $state(false);
	error = $state<string | null>(null);

	async run(load: () => Promise<unknown>): Promise<void> {
		if (!this.loaded) this.loading = true;
		try {
			await load();
			this.error = null;
			this.loaded = true;
		} catch (e) {
			if (!isAbort(e)) this.error = describeLoadError(e);
		} finally {
			this.loading = false;
		}
	}
}
