# Implementation progress

Legend: ✅ done and verified · 🟡 partially done · ⬜ not started

| Phase | Scope | Status | Verified by |
|---|---|---|---|
| 1 | Foundation: project structure, Maven/Vite/Tailwind, full Flyway schema (V1–V8), security skeleton (session + CSRF + CORS + JSON errors), health API, public pages | ✅ | 8 backend integration tests (MySQL), 6 Vitest tests, Playwright smoke run desktop + mobile |
| 2 | Authentication: register, login, logout, password reset, RBAC routing, profile, rate limiting, demo users | ⬜ | |
| 3 | Doctor & patient management, departments, doctor search | ⬜ | |
| 4 | Appointments: schedules, slot generation, concurrency-safe booking, cancel, reschedule, calendar | ⬜ | |
| 5 | Doctor consultations & patient history access | ⬜ | |
| 6 | Prescriptions (draft/finalise/version, PDF) | ⬜ | |
| 7 | Reception: walk-ins, check-in, queue tokens | ⬜ | |
| 8 | Laboratory module | ⬜ | |
| 9 | Pharmacy & inventory | ⬜ | |
| 10 | Billing & payments | ⬜ | |
| 11 | Notifications, e-mail, reminders, next-slot suggestions | ⬜ | |
| 12 | Analytics, admin, settings, reports, audit viewer, exports | ⬜ | |
| 13 | UI polish & accessibility | ⬜ | |
| 14 | Testing, deployment & documentation package (SRS, diagrams, viva Q&A) | ⬜ | |

## Feature-completion checklist (Definition of Done)

A feature is ticked only when **all** of these hold:

- [ ] Frontend page exists and is reachable from navigation
- [ ] Backend endpoint(s) exist and use DTOs
- [ ] Data persists in MySQL and is read back correctly
- [ ] Authentication + role + resource-level authorization enforced on the server
- [ ] Valid and invalid input handled (400/409/422 with field messages)
- [ ] Loading, empty and error states in the UI
- [ ] Automated tests exist and were run
- [ ] Integrates with related modules (notifications, audit, billing …)
- [ ] README / module docs updated

## Final acceptance workflow (Part 24) — tracking

| # | Step | Phase | Status |
|---|---|---|---|
| 1–2 | Patient registers and logs in | 2 | ⬜ |
| 3–4 | Search cardiologist, view profile | 3 | ⬜ |
| 5–8 | Select slot, book, concurrent booking rejected, dashboard shows booking | 4 | ⬜ |
| 9 | Receptionist checks patient in | 7 | ⬜ |
| 10–13 | Doctor sees appointment, opens record, records consultation | 5 | ⬜ |
| 14–15 | Prescription finalised, patient downloads PDF | 6 | ⬜ |
| 16–18 | Lab order, report upload, patient download | 8 | ⬜ |
| 19 | Pharmacist dispenses | 9 | ⬜ |
| 20–22 | Invoice, payment, patient views invoice | 10 | ⬜ |
| 23 | Notifications received | 11 | ⬜ |
| 24–25 | Admin views statistics and reports | 12 | ⬜ |
| 26 | Unauthorized access blocked | 2–12 | ⬜ |
