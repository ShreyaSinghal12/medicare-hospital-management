import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { expect, test } from '@playwright/test';
import { login, USERS } from './helpers.js';

const AXE = readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'), 'utf8');
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'];

const PAGES = {
  public: ['/', '/about', '/departments', '/doctors', '/login', '/register'],
  patient: ['/patient', '/patient/appointments', '/patient/history', '/patient/prescriptions', '/patient/lab-reports', '/patient/invoices', '/account'],
  doctor: ['/doctor', '/doctor/appointments', '/doctor/patients', '/doctor/lab-orders', '/doctor/availability'],
  reception: ['/reception', '/reception/appointments', '/reception/queue', '/reception/patients', '/reception/billing'],
  admin: ['/admin', '/admin/doctors', '/admin/inventory', '/admin/inventory/alerts', '/admin/dispensing/new', '/admin/billing',
    '/admin/users', '/admin/reminders', '/admin/audit', '/admin/settings', '/admin/reports'],
};

for (const [role, paths] of Object.entries(PAGES)) {
  test(`no WCAG 2.1 AA violations: ${role} pages`, async ({ page }) => {
    if (role !== 'public') await login(page, USERS[role]);
    const problems = [];
    for (const path of paths) {
      await page.goto(path);
      await page.locator('h1').first().waitFor();
      await page.waitForLoadState('networkidle');
      await page.addScriptTag({ content: AXE });
      const violations = await page.evaluate(async (tags) => (await window.axe.run(document, { runOnly: { type: 'tag', values: tags } }))
        .violations.map((v) => `${v.id}: ${v.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' | ')}`), TAGS);
      violations.forEach((v) => problems.push(`${path} ${v}`));
    }
    expect(problems).toEqual([]);
  });
}
