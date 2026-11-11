import { expect } from '@playwright/test';

export const USERS = {
  admin: 'admin@medicare.example',
  reception: 'reception@medicare.example',
  doctor: 'dr.rao@medicare.example',
  patient: 'anita.patient@medicare.example',
};

/** The demo password comes from the environment (the same DEMO_PASSWORD the backend uses); it is never stored here. */
export function demoPassword() {
  const pw = process.env.DEMO_PASSWORD;
  if (!pw) throw new Error('Set DEMO_PASSWORD to the demo account password from backend/.env');
  return pw;
}

export async function login(page, email) {
  await page.goto('/login');
  await page.getByLabel(/E-mail address/).fill(email);
  await page.getByLabel(/^Password/).fill(demoPassword());
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).not.toHaveURL(/\/login/);
  // wait for the role's home page to settle: the app may still redirect once after login
  await expect(page.locator('h1').first()).toBeVisible();
  await page.waitForLoadState('networkidle');
}

/** Calls the API from inside the page (same session cookie and CSRF token as the app). */
export async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const xsrf = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1];
    const r = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(xsrf ? { 'X-XSRF-TOKEN': decodeURIComponent(xsrf) } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: r.status, json: r.headers.get('content-type')?.includes('json') ? await r.json() : null };
  }, { method, path, body });
}

export function hospitalToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
}
