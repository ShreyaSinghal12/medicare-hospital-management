# Testing

Three automated layers. All test data is synthetic.

| Layer | Tools | Runs against | Count |
|---|---|---|---|
| Backend | JUnit 5, Mockito, Spring Boot Test, MockMvc | Real MySQL test database `medicare_test` (Flyway-migrated) | 150 tests in 23 classes |
| Frontend | Vitest, React Testing Library, jsdom | Components and routes, API mocked at the service boundary | 85 tests in 20 files |
| End-to-end | Playwright (Chromium) + axe-core 4.10 | The real stack: Vite → Spring Boot → MySQL with demo data | 10 tests |

## How to run

**Backend.** Needs MySQL and `TEST_DB_*` in `backend/.env`.

```bash
cd backend
mvn test
```

**Frontend unit tests and the production build:**

```bash
cd frontend
npm test
```

```bash
cd frontend
npm run build
```

**End-to-end.** First start the backend with the demo data and a higher login rate limit:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev,demo LOGIN_RATE_LIMIT=500 mvn spring-boot:run
```

Then, in a second terminal:

```bash
cd frontend
npx playwright install chromium
DEMO_PASSWORD='the value from backend/.env' npm run e2e
```

* The journey test registers a new fictional patient on each run. It saves Dr. Rao's availability first and restores it afterwards.
* It needs dispensable stock of "Paracetamol (fictional)" 500 mg. If a run fails at the dispensing step, add a batch in *Medicines*.

## What the backend tests cover

| Class | Tests | Focus |
|---|---|---|
| `AccessMatrixIntegrationTest` | 1 (240 checks) | 48 endpoints × {anonymous, patient, doctor, receptionist, admin}: expects 401/403, or "allowed" (anything except 401/403) |
| `AuthIntegrationTest` | 18 | Register, login, lockout, rate limit, reset tokens, session revocation, audit |
| `AppointmentIntegrationTest` | 13 | Slot rules, booking window, cancel/reschedule limits, **10 parallel bookings → exactly 1 wins** |
| `DirectoryAndAdminIntegrationTest` | 12 | Doctor directory, departments, staff accounts, patient corrections and duplicates |
| `ReceptionIntegrationTest` | 9 | Walk-ins, check-in tokens (**parallel check-ins → unique tokens**), queue, no clinical data for reception |
| `ConsultationIntegrationTest` | 8 | Treatment relationship, draft/finalize/amend with version check, denial audit |
| `PrescriptionIntegrationTest` | 7 | Finalize is immutable, revisions supersede, PDF, no access for reception/admin |
| `FoundationIntegrationTest` | 7 | Health, schema, CSRF, JSON error format |
| `LabIntegrationTest` | 6 | Catalog, order lifecycle, PDF upload safety, release to patient, 403 for reception/admin |
| `BillingIntegrationTest` | 6 | Recorded prices, double-billing guard, **simultaneous payments**, refunds, card-number rejection |
| `ReminderIntegrationTest` | 6 | Once-only claim, quiet hours, preferences, skipped inactive accounts |
| `InventoryIntegrationTest` | 5 | Batches, ledger, alerts, **parallel dispensing cannot oversell**, strength matching |
| `AdminAnalyticsIntegrationTest` | 5 | Dashboard aggregates, settings audit, CSV/PDF reports, formula-injection escaping |
| `CsrfCookieIntegrationTest` | 1 | XSRF cookie attributes |
| Unit tests (9 classes) | 46 | Status transitions (16), password policy (11), slot calculator, PDF validator, CSV writer, rate limiter, auth service, **production safety check**, **admin bootstrap** |

## End-to-end suite (`frontend/e2e`)

| Spec | What it proves |
|---|---|
| `public.spec.js` (3) | Public pages show live data; unknown routes show 404; protected routes ask for login; the login error does not reveal which field was wrong |
| `journey.spec.js` (1) | One patient across all four roles, in a real browser: **register → book via next available → reception check-in → doctor consultation → prescription finalized → lab order, result, PDF upload, release → admin dispensing → reception invoice and payment → patient sees prescription, report and paid invoice** |
| `access.spec.js` (1) | A receptionist gets *Access denied* on admin, doctor and patient pages and 403 from their APIs |
| `a11y.spec.js` (5) | axe-core WCAG 2.0/2.1 A + AA + best-practice on 34 pages across public, patient, doctor, reception and admin: 0 violations |

## Latest results (2026-11-11)

| Suite | Command | Result |
|---|---|---|
| Backend | `mvn test` | **150 passed**, 0 failures, 0 errors, 0 skipped |
| Frontend unit | `npm test` | **85 passed** (20 files) |
| Frontend build | `npm run build` | Succeeded; main bundle 363 kB (112 kB gzip) |
| End-to-end | `npm run e2e` | **10 passed** in 1.4 min |
| Diagrams | Mermaid 11 `parse()` in Chromium | 12 diagrams in `docs/`, 0 syntax errors |

## Manual verifications (earlier phases)

* **Deployment:**
  * the production jar refused to start with unsafe settings and started with safe ones; Swagger and `/v3/api-docs` returned 404;
  * behind nginx over HTTPS, a browser login produced Secure, HttpOnly session and Secure XSRF cookies, and each security header was sent once;
  * the first-admin bootstrap was run on an empty database.
* **Phase 4:** two browsers raced for one slot; one got the booking and the other a clear conflict message.
* **Phase 13:** keyboard-only navigation; the browser was set to UTC−11 to check that hospital-time "today" is used.

## Not tested

* `docker compose up`: there was no Docker daemon. `docker compose config` validates the file.
* Load or performance testing.
* Browsers other than Chromium.
* Screen readers with real users.
