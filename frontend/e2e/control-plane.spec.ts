import { expect, test } from '@playwright/test';

const admin = { email: 'admin@dcp.local', password: 'ChangeMe!2026' };

test('an anonymous visit to the dashboard returns to login', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible();
});

test('a wrong password shows the API error', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(admin.email);
  await page.getByLabel('Mot de passe').fill('not-the-password');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.locator('.error-banner')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test('login opens the dashboard and logout returns to login', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(admin.email);
  await page.getByLabel('Mot de passe').fill(admin.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
  await page.getByRole('link', { name: 'Applications' }).click();
  await expect(page).toHaveURL(/\/applications$/);
  await expect(page.getByRole('link', { name: 'hello-dcp' })).toBeVisible();
  await page.getByRole('button', { name: 'Administrator' }).click();
  await page.getByRole('menuitem', { name: 'Déconnexion' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/applications');
  await expect(page).toHaveURL(/\/login$/);
});

test('creating an application shows it in the list', async ({ page }) => {
  const name = `e2e-${Date.now().toString(36)}`;
  await page.goto('/login');
  await page.getByLabel('Email').fill(admin.email);
  await page.getByLabel('Mot de passe').fill(admin.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.goto('/applications/new');
  await page.locator('input[formcontrolname="name"]').fill(name);
  await page.locator('input[formcontrolname="gitRepositoryUrl"]').fill('https://github.com/octocat/Hello-World');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page).toHaveURL(/\/applications\/[0-9a-f-]{36}$/);
  await page.goto('/applications');
  await expect(page.getByRole('link', { name })).toBeVisible();
});
