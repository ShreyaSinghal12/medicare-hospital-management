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

All other `/api/**` paths require login (they return JSON `401` until Phase 2 adds authentication).

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

## 10. Security limitations (academic prototype)

Before any real-world use this system would need, at minimum: a professional penetration test, a
regulatory/privacy compliance review (e.g. HIPAA / DPDP Act / GDPR as applicable), encryption at rest for the
database and uploaded files, TLS everywhere, centralised log monitoring, backup/disaster-recovery procedures,
dependency vulnerability scanning in CI, multi-factor authentication for staff, and a formal clinical-safety review.
