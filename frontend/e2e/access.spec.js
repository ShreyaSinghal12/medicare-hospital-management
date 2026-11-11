import { expect, test } from '@playwright/test';
import { api, login, USERS } from './helpers.js';

test('each role is kept out of the other roles\' pages and APIs', async ({ page }) => {
  await login(page, USERS.reception);
  for (const path of ['/admin/reports', '/doctor', '/patient']) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
  }
  expect((await api(page, 'GET', '/api/admin/audit')).status).toBe(403);
  expect((await api(page, 'POST', '/api/prescriptions', {})).status).toBe(403);
  expect((await api(page, 'GET', '/api/patients/me/medical-history')).status).toBe(403);
});
