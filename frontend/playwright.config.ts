import { defineConfig } from '@playwright/test';

// Pruebas de extremo a extremo para 2 o 3 flujos clave (AGENTS.md, sección 3.1).
// Corren contra el build con MSW, sin backend.
export default defineConfig({
	testDir: 'e2e',
	webServer: {
		command: 'npm run build && npm run preview -- --port 4173',
		port: 4173,
		env: { PUBLIC_API_MOCK: 'true' },
		reuseExistingServer: !process.env.CI
	},
	use: {
		baseURL: 'http://localhost:4173',
		locale: 'es-CO',
		timezoneId: 'America/Bogota'
	}
});
