# User Guide

MediCare has four roles. Each person sees only the menu for their own role. All names and data in this guide are fictional.

**Everyone:**
* **Sign in** at `/login`. Five wrong passwords lock the account for 15 minutes.
* **Forgot password** sends a single-use link that is valid for 30 minutes.
* **Notifications** (bell icon) lists in-app messages.
* **Account settings** lets you change your password and e-mail preferences. Security e-mails are always sent.
* **Sign out** ends the session on the server. An idle session expires after 30 minutes.
* All dates and times are shown in the **hospital's time zone**, whatever your device clock says.

---

## 1. Patient

| Task | Where | Notes |
|---|---|---|
| Create an account | Home → *Register* | Password: 8–64 characters with upper-case, lower-case, a digit and a symbol. |
| Find a doctor | *Find a doctor* | Search by name, filter by department, open a profile to see fees and hours. |
| Book | Doctor profile → pick a date → choose a slot (or *Next available*) → *Continue to book* → reason → confirm | If someone else took the slot a moment earlier, you are told and can choose another one. |
| Reschedule / cancel | *My appointments* | Allowed online up to the cancellation window (default 2 h before), at most 3 reschedules. Reception can always help. |
| Medical history | *Medical history* | Finalized visits, diagnoses, active allergies and conditions. Book a recommended follow-up from here. |
| Prescriptions | *Prescriptions* | Finalized prescriptions only. Download the PDF. Older versions are marked *Superseded*. |
| Lab reports | *Lab reports* | The status is shown at once; results and the PDF appear when the doctor releases the report. |
| Invoices | *Invoices* | Items, payments, refunds, balance. Download the PDF. |
| Profile | *My profile* | Contact details, date of birth, emergency contact. |

## 2. Doctor

| Task | Where | Notes |
|---|---|---|
| Today's queue and schedule | *Dashboard*, *Appointments* | Checked-in patients show a token number. Week calendar view. |
| Start a consultation | Appointment → *Start consultation* | Possible on the appointment day. Without a reception check-in, starting records an implicit check-in. |
| Record the visit | Consultation page | Complaints, observations, diagnosis, notes, follow-up date. *Save draft* any time; *Finalize* needs a diagnosis and completes the appointment. |
| Amend a finalized visit | Consultation → *Amend* | A reason is required; the previous values are kept. |
| Allergies and conditions | Patient record | Add, update or mark inactive. |
| Prescription | Consultation → *Prescription* | Type every medicine line yourself. *Finalize* locks it and notifies the patient. *Revise* creates a new version with a reason. |
| Lab tests | Consultation → *Order lab tests*, then *Lab orders* | Mark the sample as collected, type results, upload the PDF report (≤ 5 MB), then *Finalize* to release it to the patient. |
| My patients | *My patients* | Only patients you have a non-cancelled appointment with. Every record view is audited. |
| Availability | *Availability* | Weekly hours, break, consultation length; request leave (an administrator approves it). |

MediCare never suggests a diagnosis, treatment, medicine or dose. Everything clinical is typed by you.

## 3. Receptionist

| Task | Where | Notes |
|---|---|---|
| Register a walk-in | *Register walk-in* | No password is needed: the patient gets a "set your password" e-mail (72 h). |
| Find or update a patient | *Patients* | Contact details only; no clinical data is visible to reception. |
| Book for a patient | *Book appointment* | Same double-booking protection as online booking. Today's bookings can be marked as walk-ins. Print the confirmation. |
| Check in | *Reception desk* or *Appointments* → *Check in* | Same day only. Issues the next token for that doctor. |
| Queue | *Queue* | Live per-doctor list. *Skip* a waiting patient and *Put back* later (same token). |
| Cancel / no-show | *Appointments* | The patient's message includes the doctor's next free time. |
| Create an invoice | *New invoice* → choose patient → tick unbilled charges, add extra items, discount (with reason) | Prices come from the server (recorded at booking, ordering or dispensing). |
| Take payment | Invoice → *Record payment* | Cash or *simulated* UPI / card / bank transfer with a reference only. Never type card numbers. |
| Refund / cancel | Invoice → *Refund* / *Cancel* | Both need a reason. Cancelling an unpaid invoice releases its charges for re-billing. |

## 4. Administrator

| Task | Where | Notes |
|---|---|---|
| Overview | *Dashboard* | Counts and amounts only; every chart has a *Show data* table. |
| Reports | *Reports* | Appointments, payments, invoices, lab workload, stock, audit. Preview, CSV or PDF; up to 366 days. |
| Doctors, departments, staff | *Doctors*, *Departments*, *Staff accounts* | New staff get a set-password link. Records are deactivated, never deleted. |
| Leave | *Leave requests* | Approving leave flags affected appointments as *needs rescheduling* and notifies patients. |
| Users | *Users & access* | Activate, deactivate, unlock accounts. |
| Lab catalog | *Lab tests* | Code, name, sample type, price, turnaround, unit and reference range (for information only). |
| Medicines | *Medicines*, *Stock alerts*, *Stock history* | Add batches (expiry, supplier), adjust, return, write off expired stock. Alerts for low and expiring stock. |
| Dispense | *Dispense* → enter the `RX-…` number | Only the matching medicine and strength, non-expired batches (earliest expiry first), never more than prescribed. Print the record. |
| Billing overview | *Billing summary*, *Invoices* | Read-only; administrators cannot record payments. |
| Reminders | *Reminders* | Lead time, e-mail on/off, *Send due reminders now*, reminder log. |
| Audit log | *Audit log* | Read-only search. |
| Hospital settings | *Hospital settings* | Name, address and contact, default slot length, booking window, online cancellation window and policy, invoice prefix, tax rate, expiry warning days, reminder lead time. Every change is audited. The time zone is fixed at installation. |

Administrators cannot open consultations, diagnoses, clinical notes or lab results. The dispensing view shows only the medicine lines.
