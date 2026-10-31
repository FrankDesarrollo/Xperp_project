import { expect, test, type Page } from '@playwright/test';

async function login(page: Page) {
	await page.goto('/login');
	await page.getByLabel('Contraseña').fill('Xperp2026*');
	await page.getByRole('button', { name: 'Entrar' }).click();
	await expect(page).toHaveURL(/\/tablero$/);
}

test('el tablero muestra lo que recomienda la IA y lleva a Inventario con el porqué', async ({
	page
}) => {
	await login(page);
	await expect(page.getByRole('heading', { name: 'Qué recomienda la IA hoy' })).toBeVisible();
	await expect(page.getByText('Trasladar 72 und desde CAL-01')).toBeVisible();

	await page.getByRole('link', { name: 'Ver las 3 recomendaciones' }).click();
	await expect(page).toHaveURL(/\/inventario\?tab=recomendaciones$/);
	await expect(page.getByText(/Granos del Valle S\.A\.S\. tiene el mejor puntaje/)).toBeVisible();
	await expect(page.getByText('No reponer: sobran 165 und')).toBeVisible();
});

test('desde cualquier pantalla se le puede preguntar a la IA y cerrar con Escape', async ({
	page
}) => {
	await login(page);
	await page.getByRole('button', { name: 'Preguntar a la IA' }).click();

	const chat = page.getByRole('dialog', { name: 'Asistente de Xperp' });
	await chat.getByRole('button', { name: '¿Qué me recomiendas reponer?' }).click();
	await expect(chat.getByText(/CPE-006 Pañales etapa 3 x 30 en BOG-01/)).toBeVisible();

	await page.keyboard.press('Escape');
	await expect(chat).toBeHidden();
});
