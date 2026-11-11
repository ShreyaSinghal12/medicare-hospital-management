# Known Limitations

MediCare is an **academic prototype** using synthetic data. It is **not** certified for real clinical use.

## Scope (by design)

* **No clinical decision support.** There are no suggested diagnoses, treatments, drug interactions, dose checks or abnormal-value flags. This is intentional: medical decisions are entered by an authorised doctor.
* **Four roles only.** There are no separate laboratory or pharmacy logins. Doctors handle lab orders and reports; administrators handle the test catalog, stock and dispensing.
* **No payment gateway.** UPI, card and bank transfer are recorded as *simulated*, with a reference only. No card data is stored.
* **No insurance, claims or wards.** In-patient admission, beds, surgery, insurance billing and pharmacy purchase orders are out of scope.
* **Single hospital, single time zone.** The time zone is fixed at installation. There is no multi-branch support.
* **English only.** The currency format follows hospital settings; there is no translation layer.

## Security and compliance

Before any real use, the system would need at least:
* a professional penetration test;
* a compliance review (e.g. HIPAA, DPDP Act 2023 or GDPR, as applicable);
* a formal clinical-safety review.

Missing technical controls:

| Gap | Current state |
|---|---|
| Multi-factor authentication | Not implemented; staff sign in with password only. |
| Encryption at rest | Relies on the host or disk. MySQL tables and uploaded PDFs are not encrypted by the application. |
| Malware scanning | Uploads are checked for PDF structure and active content, but not scanned by antivirus. Compressed PDF streams are not inspected. |
| Rate limiting | In-memory, per server instance. Several instances would need a shared store (e.g. Redis) or a gateway limit. |
| Sessions | Stored in server memory. A restart signs everyone out, and scaling out needs sticky sessions or Spring Session JDBC/Redis. |
| Secrets | Read from environment variables or `.env`. There is no vault integration. |
| Backups / DR | Not automated. The [DEPLOYMENT.md](DEPLOYMENT.md) checklist recommends a nightly `mysqldump` plus a copy of the uploads volume. |
| Monitoring | Health endpoint and logs only. There are no metrics dashboards or alerting. |
| Dependency scanning | Not in CI (there is no CI pipeline in the repository). |
| Audit log tamper-evidence | The application cannot edit or delete entries, but a database administrator could. There is no hash chain or write-once storage. |

## Functional

* **E-mail:** sent through SMTP when configured, otherwise written to `backend/mail-outbox/` in development. There are no SMS or WhatsApp channels.
* **Reminders:** a 15-minute scheduler. A reminder can be up to about 15 minutes later than the exact lead time, and none are sent during quiet hours (21:00–08:00).
* **Reports:** limited to 366 days and 10,000 rows per export.
* **Lab reports:** PDF only, at most 5 MB, one report per order.
* **Search:** simple `LIKE` matching. There is no fuzzy search or phonetic name matching. Possible duplicates are grouped by exact name + date of birth, or by matching phone digits, and are only listed for an administrator to review (no merge).
* **Interoperability:** no HL7/FHIR, no device or LIS integration, no barcode or label printing.
* **Accessibility:** automated axe-core scans and keyboard checks were done. No manual screen-reader audit with users was carried out.

## Verification limits

* The Docker Compose stack is validated with `docker compose config`, but `docker compose up` was **not run**: the development container has no Docker daemon. The production jar was run behind a local nginx with the same configuration (TLS, headers, proxy), and that path was tested.
* Load and performance testing was not done. The concurrency tests prove correctness under parallel requests, not throughput.
* Browser testing used Chromium (Playwright). Firefox and Safari were not tested automatically.
