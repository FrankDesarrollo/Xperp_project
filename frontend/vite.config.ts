import { sveltekit } from '@sveltejs/kit/vite';
import { svelteTesting } from '@testing-library/svelte/vite';
import { defineConfig } from 'vitest/config';

export default defineConfig({
	// svelteTesting() solo actúa al correr Vitest: resuelve Svelte para el navegador
	// y limpia el DOM entre pruebas.
	plugins: [sveltekit(), svelteTesting()],
	server: {
		port: 5173,
		strictPort: true
	},
	test: {
		include: ['src/**/*.{test,spec}.{js,ts}'],
		environment: 'jsdom',
		setupFiles: ['./vitest-setup.ts']
	}
});
