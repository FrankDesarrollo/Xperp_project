import { describe, expect, it, vi } from 'vitest';
import { createRawSnippet } from 'svelte';
import { fireEvent, render, screen } from '@testing-library/svelte';
import { ApiError } from '$lib/api/client';
import { Loader } from '$lib/utils/loader.svelte';
import LoadState from './LoadState.svelte';

// El cliente lee la URL de la API del entorno de SvelteKit, que no existe al correr Vitest.
vi.mock('$env/dynamic/public', () => ({ env: {} }));

const children = createRawSnippet(() => ({ render: () => '<p>Datos listos</p>' }));

function show(loader: Loader, onretry = vi.fn()) {
	render(LoadState, { loader, what: 'los pedidos', onretry, children });
	return onretry;
}

describe('LoadState', () => {
	it('mientras carga por primera vez dice qué está cargando y no muestra datos', () => {
		show(new Loader());
		expect(screen.getByRole('status')).toHaveTextContent('Cargando los pedidos…');
		expect(screen.queryByText('Datos listos')).toBeNull();
	});

	it('si la primera carga falla, explica el error y ofrece reintentar', async () => {
		const loader = new Loader();
		await loader.run(() => Promise.reject(new TypeError('Failed to fetch')));
		const onretry = show(loader);

		expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cargar esta información');
		expect(screen.getByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor');
		expect(screen.queryByText('Datos listos')).toBeNull();
		await fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
		expect(onretry).toHaveBeenCalledOnce();
	});

	it('con datos muestra el contenido sin avisos', async () => {
		const loader = new Loader();
		await loader.run(async () => {});
		show(loader);
		expect(screen.getByText('Datos listos')).toBeInTheDocument();
		expect(screen.queryByRole('alert')).toBeNull();
	});

	it('si una recarga falla, conserva los datos y avisa encima', async () => {
		const loader = new Loader();
		await loader.run(async () => {});
		await loader.run(() => Promise.reject(new ApiError('boom', 'error', 503)));
		show(loader);
		expect(screen.getByText('Datos listos')).toBeInTheDocument();
		expect(screen.getByRole('alert')).toHaveTextContent('Se muestran los últimos datos');
	});
});
