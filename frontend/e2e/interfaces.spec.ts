import { expect, test } from '@playwright/test';

const admin = { email: 'admin@dcp.local', password: 'ChangeMe!2026' };
const reader = { email: 'reader@dcp.local', password: 'Reader!2026x' };

async function login(page: import('@playwright/test').Page, user: { email: string; password: string }): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Mot de passe').fill(user.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test('dashboard, docker, kubernetes, releases and monitoring show API data', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await login(page, admin);
  await expect(page.getByText('Pods Kubernetes', { exact: true })).toBeVisible();
  await expect(page.getByText('Pods Kubernetes dans Prometheus')).toBeVisible();
  await expect(page.getByText('Services Kubernetes')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Activité récente' })).toBeVisible();
  await expect(page.getByText('registry:5000/dcp/vote:').first()).toBeVisible();

  await page.getByRole('link', { name: 'Docker' }).click();
  await expect(page.getByRole('heading', { name: 'Conteneurs' })).toBeVisible();
  await expect(page.getByText('dcp.managed=true')).toBeVisible();
  await expect(page.getByRole('cell', { name: 'localhost:15000/dcp/vote' }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Construire' })).toBeVisible();

  await page.goto('/kubernetes/pods');
  await expect(page.getByRole('heading', { name: 'Pods', level: 1 })).toBeVisible();
  await expect(page.getByRole('cell', { name: /dcp-vote/ }).first()).toBeVisible();

  await page.goto('/kubernetes/services');
  await expect(page.getByText('ClusterIP').first()).toBeVisible();

  await page.goto('/kubernetes/ingresses');
  await expect(page.getByText('dcp-vote.dcp.local')).toBeVisible();

  await page.goto('/kubernetes/deployments');
  await expect(page.getByRole('button', { name: 'Actions' }).first()).toBeVisible();

  await page.goto('/kubernetes/namespaces');
  await expect(page.getByText('namespace de travail')).toBeVisible();
  await expect(page.getByRole('cell', { name: 'dcp' })).toBeVisible();

  await page.goto('/quality');
  await expect(page.getByRole('heading', { name: 'Qualité' })).toBeVisible();
  await expect(page.getByText('devops-control-plane')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ouvrir SonarQube' })).toBeVisible();

  await page.goto('/kubernetes/scaling');
  await expect(page.getByRole('heading', { name: 'Blue/green' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Canary' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'État réel des Deployments' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Déployer la piste' })).toBeVisible();

  await page.goto('/monitoring');
  await expect(page.getByRole('heading', { name: 'Monitoring' })).toBeVisible();
  await expect(page.getByText('Métriques JVM / actuator')).toBeVisible();
  await expect(page.getByText('Trafic HTTP des applications')).toBeVisible();
  await expect(page.getByText('joignable').first()).toBeVisible();
  expect(errors).toEqual([]);
});

test('a developer does not see administration', async ({ page }) => {
  await login(page, reader);
  await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0);
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('the shell stays usable on a narrow viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, admin);
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
  await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
  await expect(page.getByRole('link', { name: 'Docker' })).toBeVisible();
});
