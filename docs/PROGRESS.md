# Implementation progress

Legend: ✅ done and verified · 🟡 partially done · ⬜ not started

| Phase | Scope | Status | Verified by |
|---|---|---|---|
| 1 | Foundation: project structure, Maven/Vite/Tailwind, full Flyway schema (V1–V8), security skeleton (session + CSRF + CORS + JSON errors), health API, public pages | ✅ | 8 backend integration tests (MySQL), 6 Vitest tests, Playwright smoke run desktop + mobile |
| 2 | Authentication: register, login, logout, password reset, RBAC routing, account settings, patient profile, notifications API/UI, admin user activation, lockout + rate limiting, session revocation, audit logging, demo users | ✅ | 40 backend tests (25 integration on MySQL + 15 unit), 20 Vitest tests, 11-step Playwright run on the real stack |
| 3 | Doctor directory (search/filter/profile), admin doctor CRUD, departments, staff accounts with set-password links, admin patient search/corrections/duplicate review, doctor self-profile | ✅ | 53 backend tests (36 integration on MySQL + 17 unit), 29 Vitest tests, 11-step Playwright run on the real stack |
| 4 | Weekly schedules, leave approvals, rule-based slot generation, next-available, concurrency-safe booking, cancel (window), atomic reschedule, no-show, status history, patient booking flow, doctor week calendar, admin leave/schedule pages | ✅ | 86 backend tests (incl. 10-thread simultaneous booking, repeated 5×), 36 Vitest tests, 8-step Playwright run incl. two browsers racing for one slot |
| 5 | Treatment-relationship access + audit, consultation start/draft/finalize/amend with version check, allergies & conditions, doctor patient list/record, patient medical-history timeline with follow-up booking | ✅ | 94 backend tests, 40 Vitest tests, 6-step Playwright run incl. denial for another doctor, receptionist and admin |
| 6 | Prescriptions: multi-line drafts, finalize (immutable), revisions superseding earlier versions, discard, patient search/filters, version history, PDF with superseded watermark, notifications, timeline/dashboard integration | ✅ | 102 backend tests, 48 Vitest tests, 7-step Playwright run incl. PDF download and 403 for receptionist/admin |
| 7 | Reception: walk-in registration with activation e-mail, patient directory and contact updates, front-desk booking, day list, check-in with per-doctor tokens, queue board with skip/put back, automatic queue status, printable confirmation, doctor queue card | ✅ | 111 backend tests (incl. parallel check-ins), 55 Vitest tests, 10-step Playwright run on the real stack |
| 8 | Laboratory (four-role model): admin test catalog; doctors order tests from consultations, update status, type results, upload/replace/remove PDF reports and finalize them; patients see and download finalized reports; treating-doctor record view; secure file handling with audit | ✅ | 122 backend tests (incl. upload-safety and access tests), 62 Vitest tests, 9-step Playwright run on the real stack |
| 9 | Medicine inventory (admin): catalog, batches with expiry and supplier, stock in, adjustments, returns, expired write-off, append-only stock history, low-stock/expiry alerts with a daily admin notification, dispensing against finalized prescriptions (matching medicine and strength, no expired batches, no over-dispensing, no negative stock), printable dispensing record | ✅ | 127 backend tests (incl. parallel dispensing), 68 Vitest tests, 8-step Playwright run on the real stack |
| 10 | Billing (receptionists): invoices from unbilled consultations, lab tests and dispensed medicines at recorded prices, extra items, discount with reason, tax; payments (cash / simulated UPI, card, bank transfer with reference only), refunds as credits, cancellation releasing charges; invoice PDF; patient invoices and notifications; admin billing summary | ✅ | 133 backend tests (incl. simultaneous payments), 73 Vitest tests, 8-step Playwright run on the real stack |
| 11 | Notifications and reminders: per-category e-mail preferences (security always on), scheduled appointment and follow-up reminders (once-only claim, quiet hours, inactive/late bookings skipped), admin reminder settings, run-now and log, next-free-slot hints on cancellation and reschedule messages | ✅ | 139 backend tests, 75 Vitest tests, 8-step Playwright run on the real stack |
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
| 9 | Receptionist checks patient in | 7 | ✅ |
| 10–13 | Doctor sees appointment, opens record, records consultation | 5 | ✅ |
| 14–15 | Prescription finalised, patient downloads PDF | 6 | ✅ |
| 16–18 | Doctor orders lab test, doctor uploads fictional report, patient downloads | 8 | ✅ |
| 19 | Administrator records dispensing against the finalized prescription | 9 | ✅ |
| 20–22 | Invoice, payment, patient views invoice | 10 | ✅ |
| 23 | Notifications received | 2 (infrastructure), 4–11 (events) | ✅ booking, cancellation, reschedule, check-in, prescription, lab report, invoice, payment, reminders |
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

## Phase 5 notes

* No schema change was needed; the tables come from `V3`.
* Clinical access is centralised in `ClinicalAccessService`. Drafts are visible only to their author; other
  treating doctors see finalized notes.
* Concurrent edits are protected with the entity `@Version`: the client sends the version it loaded, and a
  stale version gets 409 CONCURRENT_MODIFICATION.
* The browser test ran in the evening (hospital time), so the admin temporarily extended a demo doctor's hours
  through the schedule API and restored them afterwards.

## Role model change (four roles)

* The project now has exactly four roles: PATIENT, DOCTOR, RECEPTIONIST and ADMIN.
* `V1` was edited (unreleased project) so the `users.role` CHECK lists only these roles and the two former
  staff-profile tables are gone (35 application tables). Existing local databases must be recreated.
* Admin *Staff accounts* manages receptionists and administrators only. The demo seeder creates admin,
  receptionist, five doctors and two patients.
* Phase 8 (laboratory) is done by doctors (orders, status updates, report uploads) and admins (test catalog).
  Phase 9 (inventory) is admin-managed, including dispensing records.
* Tests verify that only the four roles exist, that unknown roles are rejected by the API, and that no
  laboratory or pharmacy routes exist in the frontend.

## Phase 6 notes

* No schema change was needed; the tables come from `V3`.
* **Fix found by the tests:** changing only the medicine lines of a draft did not bump the prescription's `@Version`,
  so a stale browser tab could overwrite newer edits. Draft updates now take `PESSIMISTIC_FORCE_INCREMENT`, which
  increments immediately and returns the new version to the client.
* **UI correction made during the build:** the first version of "Write prescription" created a draft with
  placeholder text. It was replaced by an empty form that creates nothing until the doctor saves, because the system
  must not pre-fill clinical content.
* `prescription_items.medicine_id` stays empty until Phase 9 links dispensing to the inventory catalog.

## Phase 7 notes

* No new migration was needed; `queue_entries` comes from `V2`.
* **Fix found by the concurrency test:** under REPEATABLE READ two parallel check-ins could read the same highest
  token. The token is now read with a locking `SELECT MAX(...) FOR UPDATE` under the doctor lock in a
  READ_COMMITTED transaction, and a unique key backs it up.
* The visit reason is removed from appointment responses for reception and admin (server-side), not just hidden in the UI.
* The browser test widened a demo doctor's hours through the admin schedule API because it ran after the
  normal demo hours. Repeated runs also triggered the login rate limiter (HTTP 429), as designed.
* Known limitation: the frontend works out "today" and shows check-in times in the browser's time zone. This is
  right when the desk computer is in the hospital's zone (Asia/Kolkata). Phase 13 will use the hospital
  time zone from `/api/public/hospital-info` everywhere.

## Phase 8 notes

* `V10__lab_results.sql` adds the catalog unit and reference range, per-test result value and comment (with a
  copy of unit and range taken at order time), and a unique key giving one report per order. It also drops the unused
  `lab_reports.result_summary` column.
* Orders always belong to a consultation, so the ordering doctor always has a treatment relationship.
* Each change locks the order row (`SELECT … FOR UPDATE`) and checks the version the client sent. Changes that only
  touch results or the report still increase the order's version.
* A new file is deleted again if the database transaction rolls back, and a replaced or removed file is deleted
  only after the commit. Failed uploads therefore leave no files behind (tested).
* **UI fix found in the browser test:** on a phone the order page scrolled sideways because the results table
  widened its grid column. The columns now use `min-w-0` so the table scrolls inside its card.
* Lab charges (`price_at_order`) are used for invoicing in Phase 10.

## Phase 9 notes

* `V11__inventory_settings.sql` adds `medicine_batches.supplier` and `hospital_settings.expiry_warning_days`
  (default 60; editable in the settings screen planned for Phase 12).
* Prescription lines stay as the doctor wrote them; the dispensing items record which catalog medicine and batch
  were used. `prescription_items.medicine_id` is not filled in, so finalized prescriptions are never changed.
* Dispensing is tied to one prescription version. If the doctor revises a partly dispensed prescription, the new
  version starts with nothing dispensed. The administrator can see the earlier dispensing records, but quantities are not
  carried over.
* The demo seeder adds five fictional medicines, set up so that every alert type shows.
* **Test fix:** `FoundationIntegrationTest` now reads the newest migration number from the classpath instead of a
  fixed value that had to be edited in each phase.
* One test run failed once with an unexplained 409 when creating a second medicine. It did not happen again in 5
  later runs. The test helper now includes the response body in its failure message, so the cause will show if it
  happens again.
* **UI fix found in the browser test:** on a phone the medicine page scrolled sideways. The table's hidden
  "Actions" header label is absolutely positioned and escaped its scroll box; the scroll box is now `relative`.
* Medicine charges (`unit_price`) are added to invoices in Phase 10.

## Phase 10 notes

* `V12__billing_references.sql` records the consultation fee at booking (`appointments.consultation_fee`) and the
  unit price at dispensing (`dispensing_items.unit_price`). Records made earlier fall back to the current fee or price.
  It also adds the unique `invoice_items.billed_reference`.
* **Design fix found in the browser test (V13):** the first version treated a refund as reversing a payment, so a
  ₹50 refund for a returned medicine left the invoice showing ₹50 due again. `V13__invoice_refunds.sql` adds
  `invoices.refunded_amount`. `amount_paid` is now the total received, and existing rows were recomputed from the
  payments table. A refund no longer changes the balance due.
* **UI fix found in the browser test:** the payment dialog kept the previous entry when reopened, because the form
  stayed mounted with its first defaults. It now mounts fresh each time it opens; a regression test was added.
* Known limitation: a payment recorded by mistake can't be voided. It can only be refunded, which counts as a
  credit.
* The PDF writes amounts as "Rs." because the built-in PDF font has no ₹ symbol; the web pages show ₹.

## Phase 11 notes

* `V14__notification_preferences.sql` adds `notification_email_preferences` (a missing row means enabled). The
  existing `users.email_notifications_enabled` remains the master switch.
* The scheduler is switched off in tests (`medicare.reminders.enabled=false`); tests call
  `ReminderService.runDue(time)` with fixed times to check the window, quiet hours and once-only sending.
* The browser test changed one booking's `created_at` in the database, because a booking made inside the reminder
  window is skipped by design.
* Seen during the browser test, planned for Phase 13: the administrator menu now has 22 entries and needs grouping,
  and times are still shown in the browser's time zone.
