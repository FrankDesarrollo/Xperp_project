import { env } from '$env/dynamic/public';

// SPA: sin renderizado en servidor (AGENTS.md, sección 3.1)
export const ssr = false;
export const prerender = false;

export async function load() {
	if (env.PUBLIC_API_MOCK === 'true') {
		const { worker } = await import('../mocks/browser');
		await worker.start({ onUnhandledRequest: 'bypass' });
	}
}
