import { expect, test } from '@playwright/test';

test.describe('public site', () => {
  test('home, departments, doctors and the status page load with live data', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('All systems operational.')).toBeVisible();
    await page.goto('/departments');
    await expect(page.getByRole('heading', { name: 'Departments', level: 1 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Cardiology' })).toBeVisible();
    await page.goto('/doctors');
    await expect(page.getByText('Dr. Kavya Mehta').first()).toBeVisible();
    await expect(page).toHaveTitle('Find a doctor · MediCare');
  });

  test('unknown pages show 404 and protected pages ask to log in', async ({ page }) => {
    await page.goto('/no-such-page');
    await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
    await page.goto('/patient/invoices');
    await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  });

  test('login validates input and refuses wrong passwords without saying which part was wrong', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page.getByText(/required/i).first()).toBeVisible();
    await page.getByLabel(/E-mail address/).fill('nobody@medicare.example');
    await page.getByLabel(/^Password/).fill('Wrong@Pass1');
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page.getByText(/Invalid e-mail or password/i)).toBeVisible();
  });
});
