import type { CurrentUser, Role } from '$lib/api/types';

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
	role = $derived<Role | undefined>(this.user?.role);

	setSession(token: string, user: CurrentUser) {
		this.token = token;
		this.user = user;
		try {
			localStorage.setItem(TOKEN_KEY, token);
		} catch {
			// Sin almacenamiento disponible, la sesión dura lo que dure la pestaña
		}
	}

	setUser(user: CurrentUser) {
		this.user = user;
	}

	hasRole(...roles: Role[]): boolean {
		return this.user !== null && roles.includes(this.user.role);
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
