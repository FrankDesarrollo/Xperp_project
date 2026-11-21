import { expect, test, type Page } from '@playwright/test';

// ADR 0004: la IA recomienda, explica y muestra el impacto; una persona aprueba lo que mueve dinero.

async function login(page: Page) {
	await page.goto('/login');
	await page.getByLabel('Contraseña').fill('Xperp2026*');
	await page.getByRole('button', { name: 'Entrar' }).click();
	await expect(page).toHaveURL(/\/tablero$/);
}

test('una OC espera aprobación con su impacto y varias se aprueban en lote', async ({ page }) => {
	await login(page);
	await page.goto('/ia/aprobaciones');

	const oc = page.getByRole('list', { name: 'Impacto de la propuesta' }).first();
	await expect(oc.getByText('Compromete')).toBeVisible();
	await expect(oc.getByText('Ventas en riesgo si no se compra hoy')).toBeVisible();
	await expect(
		page.getByRole('heading', { name: 'Despachar PV-2026-000041 con TRP-01' })
	).toBeVisible();

	await page.getByRole('checkbox', { name: 'Marcar las 2 de esta página' }).check();
	await expect(page.getByText('2 marcadas')).toBeVisible();
	await page.getByRole('button', { name: 'Aprobar 2' }).click();
	await expect(page.getByText('2 aprobadas')).toBeVisible();
	await expect(page.getByText('No tienes aprobaciones pendientes')).toBeVisible();
});

test('Finanzas muestra el último análisis y analiza a pedido sin ejecutar nada', async ({
	page
}) => {
	await login(page);
	await page.getByRole('link', { name: 'Finanzas' }).click();
	await expect(page).toHaveURL(/\/finanzas$/);

	await expect(page.getByRole('heading', { name: 'Último análisis de la IA' })).toBeVisible();
	await expect(page.getByText('Por condición')).toBeVisible();
	await expect(
		page.getByText(/Presupuesto de Cuidado personal: se proyecta al 108,9 %/)
	).toBeVisible();
	await expect(page.getByRole('cell', { name: 'Cuidado personal' })).toBeVisible();

	await page.getByRole('button', { name: 'Analizar ahora' }).click();
	await expect(page.getByText('Análisis listo')).toBeVisible();
	await expect(page.getByText('A pedido')).toBeVisible();
});

test('IA → Decisiones dice cuándo se usó la IA y que nada financiero se ejecutó solo', async ({
	page
}) => {
	await login(page);
	await page.goto('/ia/decisiones');
	await expect(page.getByRole('heading', { name: 'Cómo se usó la IA este mes' })).toBeVisible();
	await expect(page.getByText('Acciones financieras sin aprobación')).toBeVisible();
	await expect(page.getByText('Tiene que ser 0: todo lo aprobó una persona')).toBeVisible();
});
