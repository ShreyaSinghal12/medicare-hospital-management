# Software Requirements Specification — MediCare

**Project:** MediCare — Smart Web-Based Medical Management System Using Java · **Type:** academic prototype ·
**Data:** fictional only · **Version:** 1.0 (all 14 phases complete)

## 1. Introduction

### 1.1 Purpose
This document specifies what MediCare does and the qualities it must have. It is written for the project
examiners, future maintainers and testers, and every requirement points to where it is verified (see [TESTING.md](TESTING.md)).

### 1.2 Scope
A web application for a small hospital or clinic: online booking, reception and queues, consultations,
digital prescriptions, laboratory orders and reports, medicine inventory and dispensing, billing, notifications and
reminders, and administration with reports and an audit log.

**Out of scope:** automated diagnosis, treatment or dosage suggestions (all medical decisions are entered by a doctor);
online card payments (payments are recorded with a reference, never processed); insurance claims; telemedicine;
interfaces to laboratory instruments.

### 1.3 Definitions
| Term | Meaning |
|---|---|
| Treatment relationship | The doctor has at least one non-cancelled appointment with the patient. Required for any clinical access. |
| Finalized | Locked clinical content (consultation, prescription, lab report). It can be amended or revised with a reason, never edited in place. |
| Hospital time | The hospital's time zone (`hospital_settings.time_zone`, default Asia/Kolkata). All dates and times are interpreted and shown in it. |
| FEFO | First expiry, first out: batches listed earliest expiry first when dispensing. |

## 2. Overall description

### 2.1 Users and roles (exactly four)
| Role | Main tasks |
|---|---|
| **Patient** | Register, book, reschedule or cancel appointments; view medical history, prescriptions, lab reports, invoices; manage profile and notifications |
| **Doctor** | Schedule and leave; daily queue; consultations; allergies and conditions; prescriptions; lab orders, results and reports |
| **Receptionist** | Walk-in registration, front-desk booking, check-in and queue, patient contact details, invoices, payments, refunds |
| **Administrator** | Users, doctors, departments, staff; leave approval; lab test catalog; medicine inventory and dispensing; billing overview; reminders; hospital settings; dashboard, reports, audit log |

### 2.2 Operating environment
Java 21 · Spring Boot 4.1 · MySQL 8 · React 19 single-page app in current Chrome, Edge, Firefox and Safari, on desktop
and phone (390 px wide and up). Production runs on Docker with nginx and HTTPS ([DEPLOYMENT.md](DEPLOYMENT.md)).

### 2.3 Constraints
* No secrets in the source code; configuration comes from environment variables.
* No access tokens in browser storage: an HTTP-only session cookie plus a CSRF token.
* Fictional data only; no real patient information.
* No card data stored.

## 3. Functional requirements

Each requirement is implemented and covered by automated tests (backend integration tests are named by module,
e.g. `AppointmentIntegrationTest`).

### 3.1 Accounts and security (FR-AUTH)
| ID | Requirement |
|---|---|
| FR-AUTH-1 | Patients self-register (name, date of birth, e-mail, phone, password meeting the policy, consent to fictional data). |
| FR-AUTH-2 | Log in with e-mail and password; errors never reveal whether the e-mail exists. |
| FR-AUTH-3 | Five failed logins lock the account for 15 minutes; login and reset requests are rate-limited per IP. |
| FR-AUTH-4 | Password reset by e-mail link: single use, 30 minutes, only a hash stored. |
| FR-AUTH-5 | Staff accounts are created by administrators with an unknown random password; the person sets their own through a 72-hour link. |
| FR-AUTH-6 | Deactivating a user or changing a password ends that user's other sessions. |
| FR-AUTH-7 | Every role is limited to its own area on the server (URL rules plus method and record checks). |

### 3.2 Doctors, departments and schedules (FR-SCH)
| ID | Requirement |
|---|---|
| FR-SCH-1 | Public directory of active doctors, searchable by name, department and specialization. |
| FR-SCH-2 | Weekly working hours with a break per doctor; doctors edit their own, administrators any. |
| FR-SCH-3 | Leave requests approved by an administrator; affected bookings are flagged "needs rescheduling", never cancelled automatically. |
| FR-SCH-4 | Free slots are computed from hours, breaks, leave, bookings, the booking window and the current hospital time. |

### 3.3 Appointments (FR-APT)
| ID | Requirement |
|---|---|
| FR-APT-1 | Patients book a free slot with a reason; reception books for patients (optionally as a walk-in). |
| FR-APT-2 | A slot can never be booked twice, even with simultaneous requests. |
| FR-APT-3 | Patients cancel or reschedule up to the cancellation window (default 2 h) and at most 3 times; reception can always help. |
| FR-APT-4 | Status flow SCHEDULED → CHECKED_IN → IN_PROGRESS → COMPLETED, plus CANCELLED and NO_SHOW; every change is kept in a history. |
| FR-APT-5 | The visit reason is visible only to the doctor and the patient. |

### 3.4 Reception and queue (FR-REC)
| ID | Requirement |
|---|---|
| FR-REC-1 | Walk-in registration without a password; the patient receives an invitation e-mail. |
| FR-REC-2 | Check-in on the appointment day issues the next token for that doctor; tokens are unique even with simultaneous check-ins. |
| FR-REC-3 | The queue follows the consultation automatically; reception can skip a waiting patient and put them back. |
| FR-REC-4 | Printable appointment confirmation without clinical content. |

### 3.5 Consultations and medical records (FR-CLN)
| ID | Requirement |
|---|---|
| FR-CLN-1 | A doctor records symptoms, observations, diagnosis, treatment plan, notes and follow-up; a diagnosis is required to finalize. |
| FR-CLN-2 | Finalized notes are amended only with a reason; earlier versions are kept as snapshots. |
| FR-CLN-3 | Simultaneous edits are detected (409) instead of overwriting each other. |
| FR-CLN-4 | Allergies and conditions are recorded by treating doctors. |
| FR-CLN-5 | Patients see their finalized history without the doctor's internal observations and notes. |
| FR-CLN-6 | Every clinical record access and every denial is written to the audit log. |

### 3.6 Prescriptions (FR-RX)
| ID | Requirement |
|---|---|
| FR-RX-1 | The consulting doctor writes 1–20 medicine lines (name, strength, dosage, frequency, duration, instructions, quantity); nothing is suggested or pre-filled. |
| FR-RX-2 | Finalized prescriptions are immutable; a revision creates a new version under the same number and marks the old one SUPERSEDED. |
| FR-RX-3 | Patients and treating doctors download a PDF; superseded versions carry a watermark. |

### 3.7 Laboratory (FR-LAB)
| ID | Requirement |
|---|---|
| FR-LAB-1 | Administrators maintain the test catalog (price, sample type, turnaround, unit, reference range). |
| FR-LAB-2 | Doctors order tests from a consultation and move the order through ORDERED → SAMPLE_COLLECTED → PROCESSING → COMPLETED (or CANCELLED with a reason). |
| FR-LAB-3 | Doctors type results and upload a PDF report (max 5 MB, real PDF, no scripts or embedded files, random stored name, checksum verified on download). |
| FR-LAB-4 | Patients see results and download the report only after the doctor finalizes it. |

### 3.8 Inventory and dispensing (FR-INV)
| ID | Requirement |
|---|---|
| FR-INV-1 | Medicine catalog; stock arrives as batches with expiry dates; adjustments and returns need a reason; expired stock can be written off. |
| FR-INV-2 | Every stock change is an append-only history row; stock can never go negative. |
| FR-INV-3 | Alerts for low stock, batches expiring within the warning window, and expired stock; daily summary to administrators. |
| FR-INV-4 | Dispensing against the finalized prescription only: the prescribed medicine and strength, non-expired batches (earliest expiry first), never more than prescribed or in stock. |

### 3.9 Billing (FR-BIL)
| ID | Requirement |
|---|---|
| FR-BIL-1 | Invoices from unbilled consultations, lab tests and dispensed medicines at the prices recorded at the time, plus typed extra items. |
| FR-BIL-2 | Each charge is billed once; cancelling an unpaid invoice releases its charges. |
| FR-BIL-3 | Discount with a reason; tax from settings; exact decimal arithmetic. |
| FR-BIL-4 | Payments never exceed the balance; refunds need a reason and never exceed what was received; card numbers are refused. |
| FR-BIL-5 | Patients view and download their invoices; administrators see a billing summary. |

### 3.10 Notifications and reminders (FR-NOT)
| ID | Requirement |
|---|---|
| FR-NOT-1 | In-app notifications for bookings, changes, check-in, prescriptions, lab reports, invoices and payments. |
| FR-NOT-2 | E-mail by category (appointments, reminders, results, billing) with a master switch; security e-mails always sent; e-mails never contain clinical details. |
| FR-NOT-3 | Appointment reminders a configurable number of hours before (default 24) and follow-up reminders, each sent once, never at night. |
| FR-NOT-4 | Cancellation and rescheduling messages suggest the doctor's next free time. |

### 3.11 Administration (FR-ADM)
| ID | Requirement |
|---|---|
| FR-ADM-1 | Dashboard with counts and amounts only (no clinical content). |
| FR-ADM-2 | Hospital settings with validation; every change is audited; the time zone is fixed at installation. |
| FR-ADM-3 | Reports (appointments, payments, invoices, lab workload, stock, audit) as preview, CSV (formula-injection safe) and PDF; exports audited. |
| FR-ADM-4 | Read-only audit log with filters. |

## 4. Non-functional requirements
| ID | Requirement | How it is met |
|---|---|---|
| NFR-SEC-1 | Passwords stored only as BCrypt hashes (cost 12) | `SecurityConfig.passwordEncoder` |
| NFR-SEC-2 | Session cookie HTTP-only; Secure and HTTPS in production; CSRF on every state-changing request | `SecurityConfig`, `ProductionSafetyCheck` |
| NFR-SEC-3 | Least privilege: four roles; clinical data only with a treatment relationship | `AccessMatrixIntegrationTest` (240 checks) |
| NFR-SEC-4 | No passwords, tokens or clinical details in logs or e-mails | code review; e-mail templates |
| NFR-SEC-5 | Security headers (CSP, HSTS, frame denial, nosniff) | backend and nginx ([DEPLOYMENT.md](DEPLOYMENT.md)) |
| NFR-INT-1 | No double booking, duplicate tokens, overselling or overpaying under concurrency | row locks plus unique and check constraints; parallel-request tests |
| NFR-INT-2 | Financial and clinical history is never overwritten | immutable rows, versions and amendments |
| NFR-AUD-1 | Logins, record access, denials and every change are audited | `AuditService`, audit viewer |
| NFR-ACC-1 | WCAG 2.1 AA | axe-core: 0 violations on all main pages; keyboard checks |
| NFR-USE-1 | Works on phones (390 px) without sideways scrolling | browser checks every phase |
| NFR-PERF-1 | Pages load quickly | code splitting (main bundle about 360 kB); paging on every list |
| NFR-MNT-1 | Database changes only through versioned migrations | Flyway V1–V14, `ddl-auto=validate` |
| NFR-PRIV-1 | Fictional data only | demo seeder, registration consent, PDF footers |

## 5. Assumptions
* One hospital, one time zone, one currency (INR).
* SMTP is available in production for password resets and invitations.
* Staff use the system on hospital-managed devices.
