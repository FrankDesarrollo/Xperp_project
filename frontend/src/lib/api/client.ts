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
	onResponse({ response, request }) {
		if (response.status === 401 && !request.url.endsWith('/auth/login')) {
			auth.clear();
		}
		return response;
	}
};

export const api = createClient<paths>({ baseUrl: API_URL });
api.use(authMiddleware);

/** Error de la API con el formato `{ code, message, details }`. */
export class ApiError extends Error {
	constructor(
		message: string,
		readonly code: string,
		readonly status: number,
		readonly details?: Record<string, unknown>
	) {
		super(message);
	}
}

interface ApiResult<T> {
	data?: T;
	error?: unknown;
	response: Response;
}

/** Devuelve los datos o lanza un {@link ApiError} con el mensaje del backend. */
export async function unwrap<T>(promise: Promise<ApiResult<T>>): Promise<T> {
	const { data, error, response } = await promise;
	if (error !== undefined || !response.ok) {
		const body = (error ?? {}) as {
			code?: string;
			message?: string;
			details?: Record<string, unknown>;
		};
		throw new ApiError(
			body.message ?? `Error ${response.status}`,
			body.code ?? 'error',
			response.status,
			body.details
		);
	}
	return data as T;
}

/** Descarga un PDF protegido (OC o guía) y lo abre en una pestaña nueva. */
export async function openPdf(path: string): Promise<void> {
	const response = await fetch(`${API_URL}${path}`, {
		headers: auth.token ? { Authorization: `Bearer ${auth.token}` } : {}
	});
	if (!response.ok) {
		throw new ApiError('No se pudo descargar el documento', 'download_failed', response.status);
	}
	const blob = await response.blob();
	const url = URL.createObjectURL(blob);
	window.open(url, '_blank', 'noopener');
	setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
