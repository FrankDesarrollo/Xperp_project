import { expect, test } from '@playwright/test';

test('sin sesión, la raíz lleva al login', async ({ page }) => {
	await page.goto('/');
	await expect(page).toHaveURL(/\/login$/);
	await expect(page.getByRole('heading', { level: 1, name: 'Xperp' })).toBeVisible();
});

test('el administrador entra y ve el tablero con las decisiones de la IA', async ({ page }) => {
	await page.goto('/login');
	await page.getByLabel('Contraseña').fill('Xperp2026*');
	await page.getByRole('button', { name: 'Entrar' }).click();
	await expect(page).toHaveURL(/\/tablero$/);
	await expect(page.getByRole('heading', { level: 1, name: 'Tablero' })).toBeVisible();
	await expect(page.getByText('Reabastecer BEB-001 en CAL-01 con PRV-03')).toBeVisible();
});
