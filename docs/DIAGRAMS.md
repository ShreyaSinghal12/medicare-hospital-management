# Diagrams

The system architecture and the entity-relationship diagram are in [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md),
and the deployment diagram is in [DEPLOYMENT.md](DEPLOYMENT.md). All diagrams are Mermaid; GitHub and most Markdown viewers render them.

## 1. Use cases by role

```mermaid
flowchart LR
    P(("Patient"))
    D(("Doctor"))
    R(("Receptionist"))
    A(("Administrator"))

    subgraph MediCare
        UC1["Register and log in"]
        UC2["Find a doctor and book"]
        UC3["Reschedule or cancel"]
        UC4["View history, prescriptions, lab reports, invoices"]
        UC5["Register walk-in, book for a patient"]
        UC6["Check in and manage the queue"]
        UC7["Create invoices, record payments and refunds"]
        UC8["Run consultation"]
        UC9["Write prescription"]
        UC10["Order lab tests, enter results, release report"]
        UC11["Manage schedule and leave"]
        UC12["Manage users, doctors, departments"]
        UC13["Manage lab catalog and medicine stock"]
        UC14["Dispense against a prescription"]
        UC15["Dashboard, reports, audit log, settings"]
    end

    P --- UC1 & UC2 & UC3 & UC4
    R --- UC5 & UC6 & UC7 & UC3
    D --- UC8 & UC9 & UC10 & UC11
    A --- UC12 & UC13 & UC14 & UC15
```

## 2. One visit across all roles

```mermaid
flowchart LR
    B["Patient books<br/>(or reception books a walk-in)"] --> C["Reception checks in<br/>queue token"]
    C --> K["Doctor: consultation<br/>finalized"]
    K --> RX["Doctor: prescription<br/>finalized"]
    K --> L["Doctor: lab order → result → PDF<br/>released to patient"]
    RX --> DS["Administrator dispenses<br/>(earliest-expiry batch)"]
    K & L & DS --> INV["Reception: invoice<br/>(recorded prices)"]
    INV --> PAY["Payment / refund"]
    RX & L & PAY --> V["Patient sees and downloads<br/>prescription, report, invoice"]
```

## 3. Booking without double booking

```mermaid
sequenceDiagram
    autonumber
    actor Pt as Patient
    participant UI as React app
    participant API as AppointmentService
    participant DB as MySQL
    Pt->>UI: choose slot, enter reason
    UI->>API: POST /api/appointments (session cookie + X-XSRF-TOKEN)
    API->>DB: SELECT doctor ... FOR UPDATE (serialises bookings for this doctor)
    API->>API: re-check hours, break, leave, past time, booking window, overlaps
    API->>DB: INSERT appointment_slots (UNIQUE doctor/date/time)
    API->>DB: INSERT appointments (UNIQUE active_doctor_guard/date/time)
    alt a parallel request won
        DB-->>API: unique-key violation
        API-->>UI: 409 SLOT_ALREADY_BOOKED
    else booked
        API->>DB: status history, audit entry, notifications
        API-->>UI: 201 appointment (APT-yyyyMMdd-######)
    end
```

## 4. Lab report: upload and release

```mermaid
sequenceDiagram
    autonumber
    actor Dr as Doctor
    participant API as LabOrderService
    participant V as PdfUploadValidator
    participant FS as Report storage (disk)
    participant DB as MySQL
    actor Pt as Patient
    Dr->>API: POST /api/lab-orders/{id}/report (PDF)
    API->>DB: lock order row, check ordering doctor and status
    API->>V: size ≤ 5 MB, .pdf name, %PDF- signature, %%EOF, no JavaScript/launch/embedded files
    API->>FS: write <random>.pdf (outside the web root)
    API->>DB: save report metadata + SHA-256
    Note over API,FS: on rollback the new file is deleted,<br/>a replaced file is deleted only after commit
    Dr->>API: POST /api/lab-orders/{id}/finalize
    API->>DB: report finalized, order COMPLETED, notification
    Pt->>API: GET /api/lab-orders/{id}/report
    API->>FS: read file, re-check SHA-256
    API->>DB: audit LAB_REPORT_DOWNLOADED
    API-->>Pt: PDF (attachment, no-store)
```

## 5. Dispensing without overselling

```mermaid
sequenceDiagram
    autonumber
    actor Ad as Administrator
    participant API as DispensingService
    participant DB as MySQL
    Ad->>API: GET /api/admin/dispensing/prescriptions/{RX number}
    API->>DB: current FINALIZED version, matching medicines, usable batches (earliest expiry first)
    API->>DB: audit PRESCRIPTION_VIEWED_FOR_DISPENSING
    Ad->>API: POST /api/admin/dispensing (line, batch, quantity)
    API->>DB: lock prescription row, then each batch row in id order
    API->>API: medicine and strength match, batch not expired,<br/>≤ remaining prescribed, ≤ batch stock
    API->>DB: dispensing record (DSP-…), batch stock −, history rows with balance
    API-->>Ad: printable dispensing record
```

## 6. Status flows

```mermaid
stateDiagram-v2
    direction LR
    state "Appointment" as appt {
        [*] --> SCHEDULED
        SCHEDULED --> CHECKED_IN: reception check-in (token)
        SCHEDULED --> CANCELLED
        SCHEDULED --> NO_SHOW
        CHECKED_IN --> IN_PROGRESS: doctor starts
        CHECKED_IN --> CANCELLED
        CHECKED_IN --> NO_SHOW
        IN_PROGRESS --> COMPLETED: consultation finalized
    }
```

```mermaid
stateDiagram-v2
    direction LR
    state "Lab order" as lab {
        [*] --> ORDERED
        ORDERED --> SAMPLE_COLLECTED
        SAMPLE_COLLECTED --> PROCESSING: result or report saved
        PROCESSING --> COMPLETED: report finalized (visible to patient)
        ORDERED --> CANCELLED
        SAMPLE_COLLECTED --> CANCELLED
        PROCESSING --> CANCELLED
    }
```

```mermaid
stateDiagram-v2
    direction LR
    state "Invoice" as inv {
        [*] --> UNPAID
        UNPAID --> PARTIALLY_PAID: payment < balance
        UNPAID --> PAID: payment = balance
        PARTIALLY_PAID --> PAID
        UNPAID --> CANCELLED: reason (charges released)
        PAID --> REFUNDED: all received money refunded
        PARTIALLY_PAID --> REFUNDED
    }
```

```mermaid
stateDiagram-v2
    direction LR
    state "Prescription version" as rx {
        [*] --> DRAFT
        DRAFT --> FINALIZED
        FINALIZED --> SUPERSEDED: a newer version is finalized
    }
```
