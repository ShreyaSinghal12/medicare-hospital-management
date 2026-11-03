# MediCare — Smart Web-Based Medical Management System

A college final-year project: a secure, database-driven hospital/clinic management web application built with
**Java 21 + Spring Boot 4.1** (backend), **React 19 + Vite 8 + Tailwind CSS 4** (frontend) and **MySQL 8**.

> ⚠️ **Academic prototype.** All patients, diagnoses and records are fictional. The system does not make
> diagnoses or recommend treatments or doses; clinical content is entered only by authorised doctors.
> It is not certified for real clinical use (see *Security limitations* below).

* Architecture, verified tech stack, ER diagram and design decisions: [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md)
* Phase-by-phase progress and acceptance checklist: [`docs/PROGRESS.md`](docs/PROGRESS.md)

---

## 1. Prerequisites

| Software | Version | Check with |
|---|---|---|
| JDK | 21 (LTS) | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 20.19+ or 22.12+ (Vite 8 requirement) | `node -v` |
| npm | 10+ | `npm -v` |
| MySQL Server | 8.0+ | `mysql --version` |
| Git | any | `git --version` |

IDE: IntelliJ IDEA (Community is fine) or VS Code with the *Extension Pack for Java*.

## 2. Create the MySQL databases

Log in as the MySQL root user (`mysql -u root -p`) and run:

```sql
CREATE DATABASE medicare_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE medicare_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'medicare_user'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
GRANT ALL PRIVILEGES ON medicare_db.*   TO 'medicare_user'@'localhost';
GRANT ALL PRIVILEGES ON medicare_test.* TO 'medicare_user'@'localhost';
FLUSH PRIVILEGES;
```

You do **not** create any tables by hand. Flyway creates all of them from
`backend/src/main/resources/db/migration/V1__…V8__*.sql` when the backend starts.

## 3. Configure the backend

```bash
cd backend
cp .env.example .env        # Windows: copy .env.example .env
```

Edit `backend/.env` and set `DB_PASSWORD` and `TEST_DB_PASSWORD` to the password you chose above.
To use the demo accounts (section 4b), also set `DEMO_PASSWORD` to a strong password of your choice
(8+ characters with upper-case, lower-case, a digit and a special character, e.g. `Demo@2026`).
`backend/.env` is git-ignored. Spring Boot loads it automatically via `spring.config.import` in `application.yml`.

## 4. Run the backend

```bash
cd backend
mvn spring-boot:run
```

On first start Flyway logs `Successfully applied 8 migrations`. The API listens on **http://localhost:8080**.

* Health check: http://localhost:8080/api/health
* Swagger UI: http://localhost:8080/swagger-ui.html
* OpenAPI JSON: http://localhost:8080/v3/api-docs

### 4b. Optional: fictional demo accounts

Start the backend with the extra `demo` profile instead:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev,demo mvn spring-boot:run
```

On Windows PowerShell: `$env:SPRING_PROFILES_ACTIVE="dev,demo"; mvn spring-boot:run`

This creates the accounts below once. It is idempotent and never runs together with the `prod` profile.
All of them use the password you put in `DEMO_PASSWORD`.

| Role | E-mail |
|---|---|
| Administrator | admin@medicare.example |
| Receptionist | reception@medicare.example |
| Lab technician | lab@medicare.example |
| Pharmacist | pharmacy@medicare.example |
| Doctor (Cardiology) | dr.mehta@medicare.example, dr.nair@medicare.example |
| Doctor (General Medicine) | dr.rao@medicare.example |
| Doctor (Dermatology) | dr.khan@medicare.example |
| Doctor (Orthopedics) | dr.iyer@medicare.example |
| Patient | ravi.patient@medicare.example, anita.patient@medicare.example |

### 4c. E-mail without an SMTP server (development)

With `MAIL_ENABLED=false` (the default), the `dev` profile writes every outgoing e-mail as a text file to
`backend/mail-outbox/`. To test **Forgot password**, request a reset, open the newest file in that folder and
paste the link into the browser. Set `MAIL_ENABLED=true` and the `MAIL_*` SMTP variables to send real e-mails.

## 5. Run the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. The *System status* card should show **Java backend UP** and
**MySQL database UP – Schema version V8 – Tables 38**, which proves React → Spring Boot → MySQL is connected.

## 6. Run the tests

Backend integration tests use the separate `medicare_test` database:

```bash
cd backend
mvn test
```

Frontend component/route tests:

```bash
cd frontend
npm test
```

## 7. Project structure

```
medical-management-system/
├── backend/                         Spring Boot (Maven)
│   ├── pom.xml
│   ├── .env.example
│   └── src/
│       ├── main/java/com/medicare/
│       │   ├── MedicalManagementApplication.java
│       │   ├── config/ security/ controller/ service/ repository/
│       │   ├── entity/ dto/ mapper/ enums/ exception/
│       │   └── validation/ notification/ reporting/
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/        Flyway V1..V8
│       └── test/                    JUnit 5 + Spring Boot Test
├── frontend/                        React + Vite
│   ├── package.json  vite.config.js  index.html
│   └── src/
│       ├── api/ services/ hooks/ context/ utils/
│       ├── components/ layouts/ routes/
│       └── pages/ public/ auth/ patient/ doctor/ receptionist/ admin/ laboratory/ pharmacy/
└── docs/
    ├── architecture/ARCHITECTURE.md
    └── PROGRESS.md
```

## 8. API implemented so far

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/health` | public | Backend + MySQL status, Flyway schema version, table count |
| GET | `/api/public/hospital-info` | public | Hospital name, contact, active departments |
| GET | `/api/auth/csrf` | public | Sets the `XSRF-TOKEN` cookie |
| POST | `/api/auth/register` | public | Patient self-registration (201 + patient profile) |
| POST | `/api/auth/login` | public | Starts a session; returns the current user |
| POST | `/api/auth/logout` | public | Ends the session (204) |
| GET | `/api/auth/me` | public | Current user (200), or 204 when not logged in |
| POST | `/api/auth/forgot-password` | public | Sends a reset link (always 202, same message) |
| POST | `/api/auth/reset-password` | public | Sets a new password with a single-use token |
| GET / PUT | `/api/account` | any role | Own name, phone, e-mail notification preference |
| PUT | `/api/account/password` | any role | Change own password (other sessions are signed out) |
| GET / PUT | `/api/patients/me` | PATIENT | Own demographic profile |
| GET | `/api/notifications` | any role | Own notifications (`unreadOnly`, `page`, `size`) |
| GET | `/api/notifications/unread-count` | any role | Badge count |
| PATCH | `/api/notifications/{id}/read`, `/api/notifications/read-all` | any role | Mark as read |
| GET | `/api/admin/users` | ADMIN | Search accounts (`search`, `role`, `active`, `page`, `size`) |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | Activate / deactivate (`{"active":false,"reason":"…"}`) |
| PATCH | `/api/admin/users/{id}/unlock` | ADMIN | Clear a temporary login lock |
| GET | `/api/doctors` | public | Doctor directory (`search`, `departmentId`, `specialization`, `page`, `size`) |
| GET | `/api/doctors/{id}` | public | Doctor profile (404 if inactive) |
| GET | `/api/doctors/specializations` | public | Specializations for the filter |
| GET | `/api/public/departments` | public | Active departments with doctor counts |
| GET / PUT | `/api/doctor/profile` | DOCTOR | Own phone, qualifications, bio |
| GET / POST | `/api/admin/doctors` | ADMIN | Search / create doctors (creation e-mails a set-password link) |
| GET / PUT | `/api/admin/doctors/{id}` | ADMIN | View / update doctor, department, fee, consultation length |
| GET / POST | `/api/admin/departments` | ADMIN | All departments / create |
| PUT | `/api/admin/departments/{id}` | ADMIN | Rename / describe |
| PATCH | `/api/admin/departments/{id}/status` | ADMIN | Activate / deactivate (refused while active doctors remain) |
| GET / POST | `/api/admin/staff` | ADMIN | Receptionists, lab technicians, pharmacists, admins |
| GET | `/api/admin/patients` | ADMIN | Patient search (demographics only) |
| GET | `/api/admin/patients/duplicates` | ADMIN | Possible duplicate registrations |
| PUT | `/api/admin/patients/{id}` | ADMIN | Non-clinical correction with mandatory, audited reason |
| GET | `/api/doctors/{id}/schedule` | public | Weekly working hours |
| GET | `/api/doctors/{id}/slots?date=` | public | Generated slots: AVAILABLE / BOOKED / PAST / UNAVAILABLE |
| GET | `/api/doctors/{id}/next-available` | public | Earliest free slot (204 if none in the booking window) |
| POST | `/api/appointments` | PATIENT, RECEPTIONIST, ADMIN | Book (409 `SLOT_ALREADY_BOOKED` if taken) |
| GET | `/api/appointments/me?scope=UPCOMING\|PAST\|CANCELLED` | PATIENT | Own appointments |
| GET | `/api/appointments/me/summary` | PATIENT | Dashboard counts + next appointment |
| GET | `/api/appointments/{id}`, `/{id}/history` | owner / own doctor / front desk | Details and status history |
| PATCH | `/api/appointments/{id}/cancel` | owner, own doctor, front desk | Cancel with reason; releases the slot |
| PATCH | `/api/appointments/{id}/reschedule` | owner, front desk | Atomic move to another slot |
| PATCH | `/api/appointments/{id}/no-show` | own doctor, front desk | After the start time only |
| GET / PUT | `/api/doctor/availability` | DOCTOR | Weekly hours (affected bookings flagged, never cancelled) |
| GET / POST | `/api/doctor/leave`, PATCH `/api/doctor/leave/{id}/cancel` | DOCTOR | Leave / unavailability requests |
| GET | `/api/doctor/appointments`, `/api/doctor/dashboard` | DOCTOR | Calendar data and dashboard |
| GET / PUT | `/api/admin/doctors/{id}/availability` | ADMIN | Manage a doctor's hours |
| POST | `/api/admin/doctors/{id}/leave` | ADMIN | Record leave (approved immediately) |
| GET | `/api/admin/leave`, PATCH `/api/admin/leave/{id}/decision` | ADMIN | Leave approvals |

All POST/PUT/PATCH/DELETE requests need the `X-XSRF-TOKEN` header (the browser app handles this automatically).
To test with Postman, first send `GET /api/auth/csrf`, then copy the `XSRF-TOKEN` cookie value into an
`X-XSRF-TOKEN` header. Postman keeps the session cookie for later requests.

### Authentication & security behaviour

* Passwords: BCrypt (cost 12); policy is 8–64 characters with upper-case, lower-case, a digit and a special character.
* Five wrong passwords lock the account for 15 minutes; an admin can unlock it earlier.
* Login and reset requests are rate-limited per IP address (HTTP 429 with `Retry-After`).
* Error messages never reveal whether an e-mail address is registered.
* Reset links: 256-bit random token, only its SHA-256 hash is stored, valid 30 minutes, single use.
* Deactivating a user, or changing or resetting a password, ends that user's other sessions on their next request.
* Every login, failure, lockout, logout, password change and account change is written to `audit_logs`.
* Staff accounts (doctors, receptionists, lab, pharmacy, admins) are created with a random, never-disclosed
  password; the new user receives a single-use *set your password* link (valid 72 h, `ACCOUNT_SETUP_HOURS`).
  In development the link is in `backend/mail-outbox/`.
* Doctors are never deleted (deactivate them in *Doctors* or *Users & access*); departments with active
  doctors can't be deactivated.

### Appointment scheduling rules

* Slots are generated from the doctor's weekly hours in steps of their consultation length, skipping the break.
  Approved leave or unavailability, existing bookings, past times, and dates beyond the booking window
  (`hospital_settings.booking_window_days`) are not bookable. All times are in the hospital time zone.
* **No double booking**: each booking locks the doctor's row, re-checks the slot, and reserves it in
  `appointment_slots` (unique key). `appointments` also has its own unique key on doctor, date and start
  time, ignoring cancelled rows. An automated test fires 10 simultaneous bookings and checks that exactly one wins.
* Patients can cancel or reschedule online up to `cancellation_window_hours` before the start (default 2 h),
  and reschedule at most 3 times. Reception can always help.
* Status changes follow SCHEDULED → CHECKED_IN → IN_PROGRESS → COMPLETED, plus CANCELLED / NO_SHOW. Every change
  is stored in `appointment_status_history`.
* Schedule changes and approved leave never cancel bookings. Affected appointments are flagged
  *needs rescheduling* and the patient is notified.
* The demo profile gives every demo doctor Mon–Sat 09:00–17:00 hours with a 13:00–14:00 break.

## 9. Common errors and fixes

| Symptom | Cause / fix |
|---|---|
| `Access denied for user 'medicare_user'@'localhost'` | Wrong `DB_PASSWORD` in `backend/.env`, or the GRANT statements were not run. |
| `Could not resolve placeholder 'DB_USERNAME'` | `backend/.env` is missing, or you started the app from a folder other than `backend/`. |
| `Unknown database 'medicare_db'` | Run the `CREATE DATABASE` statements in section 2. |
| `Schema-validation: missing table` | Flyway did not run: check that `spring.flyway.enabled` is true and look for Flyway errors in the log. |
| `Validate failed: Migrations have failed validation` | A migration file was edited after it was applied. Never edit applied migrations; add a new `V9__…` file instead. For a throw-away dev DB you can drop and re-create `medicare_db`. |
| Port 8080 / 5173 already in use | Stop the other process or set `SERVER_PORT` (backend) / change `server.port` in `vite.config.js`. |
| Status card says *Backend unreachable* | The backend is not running, or `VITE_BACKEND_URL` points to the wrong port. |
| `403 CSRF_TOKEN_INVALID` on POST | The page was opened before the backend started. Reload so the app fetches `/api/auth/csrf`. |
| `DEMO_PASSWORD must be set…` on start-up | You enabled the `demo` profile without setting a strong `DEMO_PASSWORD` in `backend/.env`. |
| `ACCOUNT_LOCKED` when logging in | Five wrong passwords. Wait 15 minutes, or ask an admin to unlock it in *Users & access*. |
| `429 Too Many Requests` | Login/reset rate limit hit. Wait for the time in the `Retry-After` header (or raise `LOGIN_RATE_LIMIT` in dev). |
| Reset e-mail never arrives | With `MAIL_ENABLED=false` it is written to `backend/mail-outbox/` (dev profile) instead of being sent. |

## 10. Security limitations (academic prototype)

Before any real-world use this system would need, at minimum: a professional penetration test, a
regulatory/privacy compliance review (e.g. HIPAA / DPDP Act / GDPR as applicable), encryption at rest for the
database and uploaded files, TLS everywhere, centralised log monitoring, backup/disaster-recovery procedures,
dependency vulnerability scanning in CI, multi-factor authentication for staff, and a formal clinical-safety review.
