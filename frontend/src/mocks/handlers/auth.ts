import { http, HttpResponse } from 'msw';
import type { components } from '$lib/api/schema';
import { API_URL } from '$lib/api/client';

type CurrentUser = components['schemas']['CurrentUser'];
type LoginResponse = components['schemas']['LoginResponse'];

const demoUser: CurrentUser = {
	id: 1,
	email: 'admin@laceiba.demo',
	full_name: 'Administrador Demo',
	role: 'admin',
	company_id: 1,
	warehouse_id: null
};

export const authHandlers = [
	http.post(`${API_URL}/auth/login`, () =>
		HttpResponse.json<LoginResponse>({
			access_token: 'mock-token',
			token_type: 'Bearer',
			expires_in: 7200,
			user: demoUser
		})
	),
	http.get(`${API_URL}/auth/me`, () => HttpResponse.json<CurrentUser>(demoUser))
];
