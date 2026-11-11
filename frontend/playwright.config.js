import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the real stack.
 *
 * Before running: start MySQL and the backend with the demo data, and allow more logins per IP than the default:
 *   cd backend && SPRING_PROFILES_ACTIVE=dev,demo LOGIN_RATE_LIMIT=500 mvn spring-boot:run
 * Then: cd frontend && DEMO_PASSWORD=<the demo password from backend/.env> npm run e2e
 * The Vite dev server is started automatically (or reused if already running).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1, // the journey test shares one database with the other specs
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173',
    trace: 'off',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
