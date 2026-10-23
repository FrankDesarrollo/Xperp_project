import type { components } from '$lib/api/schema';

type CurrentUser = components['schemas']['CurrentUser'];

const TOKEN_KEY = 'xperp.token';

function readToken(): string | null {
	try {
		return typeof localStorage === 'undefined' ? null : localStorage.getItem(TOKEN_KEY);
	} catch {
		return null;
	}
}

/** Sesión del usuario. Estado compartido con runes (AGENTS.md, sección 13.2). */
class AuthStore {
	token = $state<string | null>(readToken());
	user = $state<CurrentUser | null>(null);

	isAuthenticated = $derived(this.token !== null);

	setSession(token: string, user: CurrentUser) {
		this.token = token;
		this.user = user;
		try {
			localStorage.setItem(TOKEN_KEY, token);
		} catch {
			// Sin almacenamiento disponible, la sesión dura lo que dure la pestaña
		}
	}

	clear() {
		this.token = null;
		this.user = null;
		try {
			localStorage.removeItem(TOKEN_KEY);
		} catch {
			// Nada que limpiar
		}
	}
}

export const auth = new AuthStore();
