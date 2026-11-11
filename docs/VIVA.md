# Viva Preparation — Questions and Answers

Answers refer to the actual code. File paths are relative to `backend/src/main/java/com/medicare/` unless stated.

## Architecture and design

**1. Why a modular monolith and not microservices?**
One team, one database and strongly related data (an invoice references appointments, lab orders and dispensing). A monolith keeps transactions local: booking, slot reservation, status history and notification are one ACID transaction. Microservices would need distributed transactions or sagas for no benefit at this scale. The code is still split by module (controller → service → repository) so it could be separated later.

**2. Why server-side sessions instead of JWT?**
The rule was "do not store access tokens in localStorage". A session id in an `HttpOnly`, `SameSite=Lax`, `Secure` (prod) cookie cannot be read by JavaScript, so XSS cannot steal it. Logout and deactivation take effect immediately because the server owns the session. JWTs cannot be revoked without a server-side list, which removes their main advantage.

**3. If cookies are sent automatically, how do you stop CSRF?**
Spring Security `csrf.spa()`: the server sets a readable `XSRF-TOKEN` cookie, and Axios copies it into the `X-XSRF-TOKEN` header. A malicious site can make the browser send the cookie but cannot read it, so it cannot set the header. Every state-changing request without a valid header gets `403 CSRF_TOKEN_INVALID`.

**4. Why Flyway with `ddl-auto=validate`?**
The schema is versioned SQL (V1–V14) that can be reviewed and applied identically everywhere. Hibernate only *validates* that the entities match; it never alters tables. Released migrations are never edited; changes come as new files (e.g. V13 refunds).

**5. Why DTOs instead of returning entities?**
Entities contain fields some roles must not see (e.g. consultation notes), and lazy relations can serialise half the database. Each endpoint returns a record shaped for its audience, e.g. the dispensing view has medicine lines but no diagnosis.

## Security and privacy

**6. How is authorization enforced?** Three layers on the server:
1. URL rules in `security/SecurityConfig.java`, so a wrong role gets 403 before the request body is even parsed.
2. `@PreAuthorize` on each controller method.
3. Record-level checks in services, e.g. a doctor can read a record only with a treatment relationship, and a patient only their own invoice.

`AccessMatrixIntegrationTest` checks 48 endpoints × 5 identities (anonymous plus the four roles) = 240 cases. The frontend's hidden menus are convenience only.

**7. What is a "treatment relationship"?**
At least one non-cancelled appointment between that doctor and that patient. Every record view and every denied attempt is written to `audit_logs`.

**8. Why can't the administrator see diagnoses?**
Least privilege. Administrators run the hospital, not patient care. They get aggregate counts on the dashboard and, for dispensing, only the medicine lines and quantities, and each prescription lookup is audited.

**9. How are passwords and reset links stored?**
* Passwords are hashed with BCrypt, cost 12.
* A reset token is 256 random bits; only its SHA-256 hash is stored. It is single-use and valid for 30 minutes.
* Error messages never reveal whether an e-mail exists.
* Five failures lock the account for 15 minutes, and login is rate-limited per IP (HTTP 429).

**10. How are uploaded PDFs made safe?**
`PdfUploadValidator` accepts at most 5 MB and requires a `.pdf` name, the `%PDF-` signature and an `%%EOF` marker. It rejects JavaScript, launch actions, embedded files and rich media. The file is stored under a random name outside the web root, the user's file name is never used as a path, and the stored SHA-256 is re-checked on every download.

**11. What do you never log?**
Passwords, reset tokens, session ids and clinical content. Audit entries record who did what to which record id, not the medical details. E-mails say "a new lab report is available", never the result.

**12. How do you handle payments without storing card details?**
There is no payment gateway. Non-cash methods are labelled *simulated* and only a reference is stored, such as a UPI transaction id or a terminal slip number. Any reference with 13 or more consecutive digits is rejected as a possible card number, both in the browser and on the server.

## Concurrency and data integrity

**13. Two patients click the same slot at the same moment. What happens?**
1. `SELECT … FOR UPDATE` on the doctor row serialises their bookings.
2. The service re-checks the slot.
3. `appointment_slots` has `UNIQUE(doctor, date, time)`.
4. `appointments` has a generated column `active_doctor_guard`, which is NULL for cancelled rows, with its own unique key. So even buggy service code cannot create a double booking.

The loser gets `409 SLOT_ALREADY_BOOKED`. A test fires 10 parallel bookings and asserts exactly one succeeds. The Playwright suite also raced two browsers in Phase 4.

**14. Why a generated column instead of a simple unique key on (doctor, date, time)?**
Cancelled appointments must keep their row for history, and the slot must become bookable again. MySQL has no partial indexes, so the column holds `doctor_id` only while the appointment is active; NULLs don't collide in a unique index.

**15. How are readable numbers like `INV-…` generated safely?**
The `number_sequences` table is read with `SELECT … FOR UPDATE` inside the transaction. Using `MAX(id)+1` would give two concurrent transactions the same number.

**16. How do you prevent overselling stock?**
The prescription row is locked, then every batch involved, always in id order to avoid deadlocks. The service checks the quantity, and a database `CHECK (quantity_available >= 0)` backs it up. Every change also appends a history row with the balance after it. This was tested with parallel dispensing requests.

**17. How is a payment kept from exceeding the invoice?**
The invoice row is locked, then the service compares the payment with the balance due. A `CHECK (amount_paid <= total_amount)` constraint is the final guard. Money is `DECIMAL` in MySQL and `BigDecimal` in Java, never floating point.

**18. Why do invoices copy prices instead of joining the price tables?**
History must not change. Fees and prices are recorded at booking, lab ordering and dispensing, and the invoice uses those values. The browser sends only *which* charges to bill; the server looks up every amount, so a user cannot edit a price in the request.

**19. How can a finalized prescription be corrected?**
It can't be edited. *Revise* creates version 2 under the same RX number with a mandatory reason. Finalizing version 2 marks version 1 `SUPERSEDED`; it is kept, and its PDF is watermarked. Consultations work the same way through `consultation_amendments`, with a JSON snapshot of the old values.

## Medical-safety scope

**20. Does the system suggest diagnoses or doses?**
No. This is a deliberate rule. Every diagnosis, medicine, dose and result is typed by the authorised doctor. Lab reference ranges are shown for information only; values are not flagged. The follow-up reminder includes the doctor's next free time, which is a scheduling hint, not clinical advice.

**21. Is the data real?**
No. All demo and test data is synthetic: invented names (e.g. "Ravi Kumar") on the reserved `medicare.example` domain, and test medicines labelled "(fictional)".

## Time and scheduling

**22. How do you handle time zones?**
* Audit timestamps are stored in UTC; the JDBC session time zone is forced to UTC.
* Appointment dates and times are local to the hospital's zone, stored in `hospital_settings.time_zone`.
* The frontend loads that zone at startup, and "today" and "has it started?" use it, not the device clock. This was tested with the browser set to UTC−11.
* The zone is fixed at installation, because changing it would shift every stored appointment.

**23. How do reminders avoid being sent twice when several servers run?**
Each reminder is *claimed* by inserting a row with a unique key, in its own transaction, before sending. A second server's insert fails, so it skips. There are also quiet hours (21:00–08:00), and inactive accounts are skipped.

## Testing and deployment

**24. How did you test it?**
* Backend: JUnit 5 + MockMvc integration tests against a real MySQL database (`medicare_test`), including the 240-case access matrix and the concurrency tests.
* Frontend: Vitest + React Testing Library component tests.
* Playwright end-to-end tests: the full patient → reception → doctor → lab → dispensing → billing journey on the real stack, an access test across roles, and an axe-core accessibility scan.

[TESTING.md](TESTING.md) has the commands and the latest results.

**25. How is it deployed?**
Docker Compose runs three containers:
* MySQL 8.4;
* the Spring Boot jar, running as a non-root user;
* nginx, which serves the built React app, terminates TLS, adds HSTS/CSP headers and proxies `/api`.

The `prod` profile refuses to start with unsafe settings (`ProductionSafetyCheck`), such as the demo profile, a short database password, non-Secure cookies, non-https origins or Swagger enabled. There are no default admin credentials: `AdminBootstrap` creates the first admin from an e-mail address, and that person sets their own password through a single-use link. See [DEPLOYMENT.md](DEPLOYMENT.md).

**26. What would you improve next?**
See [LIMITATIONS.md](LIMITATIONS.md). The main items are MFA for staff, encryption at rest, antivirus scanning of uploads, a real payment gateway with tokenisation, HL7/FHIR interoperability, and a professional security and compliance review.
