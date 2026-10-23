import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import ts from 'typescript-eslint';
import svelteConfig from './svelte.config.js';

export default ts.config(
	{
		ignores: [
			'.svelte-kit/',
			'build/',
			'node_modules/',
			'static/mockServiceWorker.js',
			'src/lib/api/schema.d.ts',
			'playwright-report/',
			'test-results/'
		]
	},
	js.configs.recommended,
	...ts.configs.recommended,
	...svelte.configs.recommended,
	prettier,
	...svelte.configs.prettier,
	{
		languageOptions: {
			globals: { ...globals.browser, ...globals.node }
		},
		rules: {
			// Las llamadas HTTP pasan por lib/api/client.ts (AGENTS.md, sección 13.2)
			'no-restricted-globals': [
				'error',
				{ name: 'fetch', message: 'Usa el cliente de lib/api/client.ts.' }
			]
		}
	},
	{
		files: ['src/lib/api/**', 'src/mocks/**'],
		rules: { 'no-restricted-globals': 'off' }
	},
	{
		files: ['**/*.svelte', '**/*.svelte.ts', '**/*.svelte.js'],
		languageOptions: {
			parserOptions: {
				projectService: true,
				extraFileExtensions: ['.svelte'],
				parser: ts.parser,
				svelteConfig
			}
		}
	}
);
