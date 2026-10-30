export type ThemeChoice = 'system' | 'light' | 'dark';

const KEY = 'xperp.theme';

/** Tema claro, oscuro o del sistema (AGENTS.md, 11.2). Se guarda en el navegador. */
class ThemeStore {
	choice = $state<ThemeChoice>('system');

	init() {
		try {
			const saved = localStorage.getItem(KEY) as ThemeChoice | null;
			if (saved) this.choice = saved;
		} catch {
			// Sin almacenamiento: tema del sistema
		}
		this.apply();
	}

	set(choice: ThemeChoice) {
		this.choice = choice;
		try {
			localStorage.setItem(KEY, choice);
		} catch {
			// Se ignora
		}
		this.apply();
	}

	cycle() {
		this.set(this.choice === 'system' ? 'dark' : this.choice === 'dark' ? 'light' : 'system');
	}

	private apply() {
		if (typeof document === 'undefined') return;
		if (this.choice === 'system') {
			document.documentElement.removeAttribute('data-theme');
		} else {
			document.documentElement.setAttribute('data-theme', this.choice);
		}
	}
}

export const theme = new ThemeStore();
