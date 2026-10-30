import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '$lib/api/client';
import { Loader, describeLoadError } from './loader.svelte';

// El cliente lee la URL de la API del entorno de SvelteKit, que no existe al correr Vitest.
vi.mock('$env/dynamic/public', () => ({ env: {} }));

describe('Loader', () => {
	it('arranca cargando y queda con datos cuando la carga termina', async () => {
		const loader = new Loader();
		expect(loader.loading).toBe(true);
		await loader.run(async () => {});
		expect(loader).toMatchObject({ loading: false, loaded: true, error: null });
	});

	it('si la primera carga falla, deja de cargar y guarda el error sin dar los datos por cargados', async () => {
		const loader = new Loader();
		await loader.run(() => Promise.reject(new TypeError('Failed to fetch')));
		expect(loader.loading).toBe(false);
		expect(loader.loaded).toBe(false);
		expect(loader.error).toMatch(/No se pudo conectar/);
	});

	it('una recarga que falla conserva los datos anteriores y muestra el error', async () => {
		const loader = new Loader();
		await loader.run(async () => {});
		await loader.run(() => Promise.reject(new ApiError('boom', 'error', 500)));
		expect(loader.loaded).toBe(true);
		expect(loader.error).toMatch(/servidor tuvo un problema/);
	});

	it('reintentar con éxito limpia el error', async () => {
		const loader = new Loader();
		await loader.run(() => Promise.reject(new TypeError('Failed to fetch')));
		await loader.run(async () => {});
		expect(loader.error).toBeNull();
		expect(loader.loaded).toBe(true);
	});

	it('una petición cancelada no se muestra como error', async () => {
		const loader = new Loader();
		await loader.run(() => Promise.reject(new DOMException('aborted', 'AbortError')));
		expect(loader.error).toBeNull();
	});
});

describe('describeLoadError', () => {
	it('un 403 dice que el rol no tiene permiso', () => {
		expect(describeLoadError(new ApiError('Forbidden', 'forbidden', 403))).toMatch(/permiso/);
	});

	it('un 4xx muestra el mensaje del backend, que ya viene en español', () => {
		expect(describeLoadError(new ApiError('El pedido no existe', 'not_found', 404))).toBe(
			'El pedido no existe'
		);
	});
});
