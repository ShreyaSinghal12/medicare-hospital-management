import { expect, test } from '@playwright/test';
import { api, hospitalToday, login, USERS } from './helpers.js';

/**
 * The full patient journey across all four roles, through the real UI:
 * register → book → check in → consultation → prescription → lab order, result and report → dispensing →
 * invoice and payment → the patient sees everything. All data is fictional and created fresh on each run.
 */
test.describe.configure({ mode: 'serial' });

const tag = Date.now().toString(36).replace(/\d/g, (d) => 'abcdefghij'[d]);
const patient = {
  name: `Test Journey ${tag}`,
  email: `journey.${tag}@test.example`,
  password: `Journey!${tag}9A`,
};
let raoId;
let savedHours;

test.beforeAll(async ({ browser }) => {
  // Give the demo doctor bookable slots for the whole day so the test can run at any hour.
  const page = await browser.newPage();
  await login(page, USERS.admin);
  raoId = (await api(page, 'GET', '/api/admin/doctors?search=Rao')).json.content[0].id;
  savedHours = (await api(page, 'GET', `/api/admin/doctors/${raoId}/availability`)).json.days;
  const days = Array.from({ length: 7 }, (_, i) => ({ dayOfWeek: i + 1, working: true, startTime: '00:00', endTime: '23:59', breakStart: null, breakEnd: null }));
  expect((await api(page, 'PUT', `/api/admin/doctors/${raoId}/availability`, { days })).status).toBe(200);
  await page.close();
});

test.afterAll(async ({ browser }) => {
  const page = await browser.newPage();
  await login(page, USERS.admin);
  await api(page, 'PUT', `/api/admin/doctors/${raoId}/availability`, { days: savedHours });
  await page.close();
});

test('a patient goes from registration to a paid invoice', async ({ browser }) => {
  const ctx = async () => (await browser.newContext({ acceptDownloads: true })).newPage();
  const pat = await ctx();
  let rxNo;

  await test.step('patient registers and books the next free slot', async () => {
    await pat.goto('/register');
    await pat.getByLabel('Full name').fill(patient.name);
    await pat.getByLabel('Date of birth').fill('1990-06-15');
    await pat.getByLabel('E-mail address').fill(patient.email);
    await pat.getByLabel('Phone number').fill('+91 98000 12345');
    await pat.getByLabel(/^Password(\s*\*)?$/).fill(patient.password);
    await pat.getByLabel('Confirm password').fill(patient.password);
    await pat.getByRole('checkbox').check();
    await pat.getByRole('button', { name: 'Create account' }).click();
    await expect(pat.getByRole('heading', { name: 'Log in' })).toBeVisible();
    await pat.getByLabel(/^Password(\s*\*)?$/).fill(patient.password);
    await pat.getByRole('button', { name: 'Log in' }).click();
    await expect(pat.getByRole('heading', { name: /Welcome, Test/ })).toBeVisible();

    await pat.goto(`/patient/doctors/${raoId}`);
    await pat.getByRole('button', { name: /Next available/ }).click();
    await pat.getByRole('button', { name: 'Continue to book' }).click();
    await expect(pat.getByRole('heading', { name: 'Book an appointment', level: 1 })).toBeVisible();
    await pat.getByLabel(/Reason for visit/).fill('Persistent cough (fictional)');
    await pat.getByRole('button', { name: /Confirm booking/ }).click();
    await expect(pat.getByRole('heading', { name: /^Appointment APT-/ })).toBeVisible();
  });

  await test.step('reception checks the patient in', async () => {
    const desk = await ctx();
    await login(desk, USERS.reception);
    await desk.goto('/reception/appointments');
    const row = desk.locator('li', { hasText: patient.name });
    await expect(row).toBeVisible();
    // the booking may be for today or a later day: check-in is only offered on the appointment day
    const appt = (await api(desk, 'GET', `/api/reception/appointments?date=${hospitalToday()}`)).json.find((a) => a.patientName === patient.name);
    expect(appt, 'next free slot should be today').toBeTruthy();
    await row.getByRole('button', { name: 'Check in' }).click();
    await expect(desk.getByText(new RegExp(`${patient.name} checked in · token \\d+`))).toBeVisible();
    await desk.context().close();
  });

  const doc = await ctx();
  await test.step('doctor records the consultation and a prescription', async () => {
    await login(doc, USERS.doctor);
    const row = doc.locator('li', { hasText: patient.name });
    await row.getByRole('button', { name: /Start consultation/ }).click();
    await expect(doc).toHaveURL(/\/doctor\/consultations\/\d+/);
    await expect(doc.getByText('Persistent cough (fictional)').first()).toBeVisible();
    await doc.getByLabel(/^Diagnosis/).fill('Acute bronchitis (fictional)');
    await doc.getByRole('button', { name: 'Finalize consultation' }).click();
    await doc.getByRole('dialog').getByRole('button', { name: 'Finalize' }).click();
    await expect(doc.getByText('Consultation finalized; appointment completed')).toBeVisible();

    await doc.getByRole('link', { name: 'Write prescription' }).click();
    await doc.getByLabel(/Medicine name/).fill('Paracetamol (fictional)');
    await doc.getByLabel('Strength').fill('500 mg');
    await doc.getByLabel(/^Dosage/).fill('1 tablet');
    await doc.getByLabel(/^Frequency/).fill('Three times daily');
    await doc.getByLabel(/^Duration/).fill('3 days');
    await doc.getByLabel('Qty').fill('9');
    await doc.getByRole('button', { name: 'Save draft' }).click();
    await expect(doc.getByText(/Draft RX-\d{8}-\d{6} saved/)).toBeVisible();
    await doc.getByRole('button', { name: 'Finalize prescription' }).click();
    await doc.getByRole('dialog').getByRole('button', { name: 'Finalize' }).click();
    await expect(doc.getByText('Prescription finalized; the patient has been notified')).toBeVisible();
    const heading = await doc.getByRole('heading', { level: 1, name: /^RX-\d{8}-\d{6}/ }).innerText();
    rxNo = heading.match(/RX-\d{8}-\d{6}/)[0];
  });

  await test.step('doctor orders a lab test, records the result, uploads and releases the report', async () => {
    await doc.goto('/doctor');
    await doc.locator('li', { hasText: patient.name }).getByRole('button', { name: 'View notes' }).click();
    await doc.getByRole('button', { name: 'Order tests' }).click();
    const dialog = doc.getByRole('dialog');
    await dialog.getByRole('checkbox', { name: /Fasting Blood Sugar/ }).check();
    await dialog.getByRole('button', { name: 'Place order' }).click();
    await expect(doc).toHaveURL(/\/doctor\/lab-orders\/\d+$/);
    await doc.getByRole('button', { name: 'Mark sample collected' }).click();
    await doc.getByLabel('Result for Fasting Blood Sugar').fill('92');
    await doc.getByRole('button', { name: 'Save results' }).click();
    await expect(doc.getByText('Results saved')).toBeVisible();
    await doc.getByLabel(/Upload report/).setInputFiles('e2e/fixtures/sample-lab-report.pdf');
    await doc.getByRole('button', { name: 'Upload', exact: true }).click();
    await expect(doc.getByText('Report uploaded')).toBeVisible();
    await doc.getByRole('button', { name: 'Finalize and release to patient' }).click();
    await doc.getByRole('dialog').getByRole('button', { name: 'Finalize' }).click();
    await expect(doc.getByText('Report released to the patient')).toBeVisible();
  });

  await test.step('administrator dispenses the prescription', async () => {
    const admin = await ctx();
    await login(admin, USERS.admin);
    await admin.goto('/admin/dispensing/new');
    await admin.getByLabel('Prescription number').fill(rxNo);
    await admin.getByRole('button', { name: 'Look up' }).click();
    await admin.getByLabel('Medicine for Paracetamol (fictional)').selectOption({ index: 1 });
    // earliest-expiry batch that still holds enough tablets (stock goes down with every run)
    const batch = admin.getByLabel('Batch for Paracetamol (fictional)');
    const options = await batch.locator('option').allInnerTexts();
    const index = options.findIndex((o) => Number(o.match(/(\d+) left/)?.[1] || 0) >= 9);
    expect(index, 'a Paracetamol batch with at least 9 tablets').toBeGreaterThan(0);
    await batch.selectOption({ index });
    await admin.getByLabel('Quantity for Paracetamol (fictional)').fill('9');
    await admin.getByRole('button', { name: 'Record dispensing' }).click();
    await expect(admin.getByRole('heading', { name: /Dispensing record DSP-/ })).toBeVisible();
    await admin.context().close();
  });

  await test.step('reception bills consultation, lab test and medicine, and takes payment', async () => {
    const desk = await ctx();
    await login(desk, USERS.reception);
    const p = (await api(desk, 'GET', `/api/reception/patients?search=${encodeURIComponent(patient.email)}`)).json.content[0];
    await desk.goto(`/reception/billing/new?patientId=${p.id}`);
    const charges = desk.locator('li').filter({ has: desk.getByRole('checkbox') });
    await expect(charges).toHaveCount(3);
    await desk.getByRole('button', { name: 'Create invoice' }).click();
    await expect(desk.getByRole('heading', { name: /INV-|Invoices/ }).first()).toBeVisible();
    await desk.getByRole('button', { name: 'Record payment' }).click();
    await desk.getByRole('dialog').getByRole('button', { name: 'Record payment' }).click();
    await expect(desk.getByText(/^Paid$/).first()).toBeVisible();
    await desk.context().close();
  });

  await test.step('patient sees the prescription, the lab report and the paid invoice', async () => {
    await pat.goto('/patient/prescriptions');
    await expect(pat.getByText(rxNo).first()).toBeVisible();
    await pat.goto('/patient/lab-reports');
    await expect(pat.locator('li', { hasText: 'Fasting Blood Sugar' }).getByText('Report ready')).toBeVisible();
    await pat.goto('/patient/invoices');
    await pat.getByRole('link', { name: /INV-/ }).first().click();
    await expect(pat.getByText(/^Paid$/).first()).toBeVisible();
    const [download] = await Promise.all([pat.waitForEvent('download'), pat.getByRole('link', { name: /PDF/ }).click()]);
    expect(download.suggestedFilename()).toMatch(/^INV-\d{8}-\d{6}\.pdf$/);
    expect((await api(pat, 'GET', '/api/admin/dashboard')).status).toBe(403);
  });
});
