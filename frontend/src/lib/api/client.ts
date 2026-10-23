/**
 * Cliente HTTP único de la aplicación. Toda llamada a la API pasa por aquí (AGENTS.md, sección 13.2).
 * Los tipos salen de `schema.d.ts`, que se genera con `npm run gen:api` y no se edita a mano.
 */
import createClient, { type Middleware } from 'openapi-fetch';
import { env } from '$env/dynamic/public';
import type { paths } from './schema';
import { auth } from '$lib/stores/auth.svelte';

export const API_URL = env.PUBLIC_API_URL ?? 'http://localhost:8080/api/v1';

const authMiddleware: Middleware = {
	onRequest({ request }) {
		if (auth.token) {
			request.headers.set('Authorization', `Bearer ${auth.token}`);
		}
		return request;
	},
	onResponse({ response }) {
		if (response.status === 401) {
			auth.clear();
		}
		return response;
	}
};

export const api = createClient<paths>({ baseUrl: API_URL });
api.use(authMiddleware);
