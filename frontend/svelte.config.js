import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	preprocess: vitePreprocess(),
	compilerOptions: {
		// Svelte 5 con runes en todo el proyecto (AGENTS.md, sección 13.2)
		runes: true
	},
	kit: {
		// SPA: todas las rutas caen en index.html y el enrutamiento ocurre en el navegador
		adapter: adapter({ fallback: 'index.html' })
	}
};

export default config;
