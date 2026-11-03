# Implementation progress

Legend: ✅ done and verified · 🟡 partially done · ⬜ not started

| Phase | Scope | Status | Verified by |
|---|---|---|---|
| 1 | Foundation: project structure, Maven/Vite/Tailwind, full Flyway schema (V1–V8), security skeleton (session + CSRF + CORS + JSON errors), health API, public pages | ✅ | 8 backend integration tests (MySQL), 6 Vitest tests, Playwright smoke run desktop + mobile |
| 2 | Authentication: register, login, logout, password reset, RBAC routing, account settings, patient profile, notifications API/UI, admin user activation, lockout + rate limiting, session revocation, audit logging, demo users | ✅ | 40 backend tests (25 integration on MySQL + 15 unit), 20 Vitest tests, 11-step Playwright run on the real stack |
| 3 | Doctor directory (search/filter/profile), admin doctor CRUD, departments, staff accounts with set-password links, admin patient search/corrections/duplicate review, doctor self-profile | ✅ | 53 backend tests (36 integration on MySQL + 17 unit), 29 Vitest tests, 11-step Playwright run on the real stack |
| 4 | Weekly schedules, leave approvals, rule-based slot generation, next-available, concurrency-safe booking, cancel (window), atomic reschedule, no-show, status history, patient booking flow, doctor week calendar, admin leave/schedule pages | ✅ | 86 backend tests (incl. 10-thread simultaneous booking, repeated 5×), 36 Vitest tests, 8-step Playwright run incl. two browsers racing for one slot |
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
| 1–2 | Patient registers and logs in | 2 | ✅ |
| 3–4 | Search cardiologist, view profile | 3 | ✅ |
| 5–8 | Select slot, book, concurrent booking rejected, dashboard shows booking | 4 | ✅ |
| 9 | Receptionist checks patient in | 7 | ⬜ |
| 10–13 | Doctor sees appointment, opens record, records consultation | 5 | ⬜ |
| 14–15 | Prescription finalised, patient downloads PDF | 6 | ⬜ |
| 16–18 | Lab order, report upload, patient download | 8 | ⬜ |
| 19 | Pharmacist dispenses | 9 | ⬜ |
| 20–22 | Invoice, payment, patient views invoice | 10 | ⬜ |
| 23 | Notifications received | 2 (infrastructure), 4–11 (events) | 🟡 registration/account notifications done |
| 24–25 | Admin views statistics and reports | 12 | ⬜ |
| 26 | Unauthorized access blocked | 2–12 | 🟡 role + own-record checks done for Phase 2 endpoints |

## Phase 2 notes

* `V9__auth_session_revocation.sql` adds `users.password_changed_at` (needed to revoke sessions after a password change or reset).
* The notification table, service and API (originally Phase 11) were built now because registration and password
  events need them. Phase 11 adds the appointment and reminder events, the scheduler and the e-mail preferences UI.
* Doctor/staff home pages show real account and notification data; each module phase replaces them with that
  role's operational dashboard.
* Admin *Users & access* covers account activation, deactivation and unlocking. Creating doctors and receptionists comes in Phase 3.

## Phase 3 notes

* No schema change was needed; the tables come from `V1`.
* Security fix found while testing: BCrypt accepts at most 72 bytes. The password policy now also limits the
  UTF-8 byte length, and over-long login or current-password input is treated as invalid credentials
  instead of causing a server error. Regression tests were added.
* Doctor working days and bookable slots on the profile page come with scheduling in Phase 4.
* Receptionist patient directory and walk-in registration come in Phase 7.
* The production JS bundle is about 500 kB (Vite warns). Route-level code splitting is planned for Phase 13.

## Phase 4 notes

* No schema change was needed; the tables come from `V2`.
* `appointment_slots` is a reservation ledger: free slots are computed, and a row is only written on booking.
* **Fix found by the concurrency test:** audit entries were written in a separate transaction (`REQUIRES_NEW`),
  needing a second pooled connection per booking. Ten simultaneous bookings exhausted the 10-connection pool.
  Audits now join the caller's transaction; only failure/denial audits that must survive a rollback still use
  their own transaction.
* **UI fix:** the slot picker's "next days" arrow jumped back to the selected date. A regression test was added.
* Check-in and queue tokens come in Phase 7; starting and completing consultations come in Phase 5. The status
  rules for those transitions are already enforced and tested.
* Doctors edit their weekly hours directly (the change applies immediately and flags affected bookings). Leave
  needs admin approval; admins can also record leave that is approved immediately.
