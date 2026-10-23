import { expect, test } from '@playwright/test';

test('la raíz redirige al tablero', async ({ page }) => {
	await page.goto('/');
	await expect(page).toHaveURL(/\/tablero$/);
	await expect(page.getByRole('heading', { level: 1, name: 'Tablero' })).toBeVisible();
});
