-- =====================================================================
-- V8: Reference (non-personal) data required by every environment.
-- Demo USERS are NOT created here — they are created by the optional
-- "demo" Spring profile so no default credentials ship to production.
-- =====================================================================

INSERT INTO hospital_settings (id, hospital_name, address, phone, email, time_zone, default_slot_minutes,
                               booking_window_days, cancellation_window_hours, cancellation_policy,
                               invoice_prefix, tax_rate_percent, email_reminders_enabled, reminder_hours_before)
VALUES (1, 'MediCare Community Hospital', '12 Example Health Avenue, Sample City', '+91-00000-00000',
        'contact@medicare.example', 'Asia/Kolkata', 15, 30, 2,
        'Appointments can be cancelled or rescheduled up to 2 hours before the scheduled time.',
        'INV', 0.00, TRUE, 24);

INSERT INTO departments (name, description) VALUES
    ('General Medicine', 'Primary care, common illnesses and preventive health check-ups.'),
    ('Cardiology',       'Heart and cardiovascular system care.'),
    ('Dermatology',      'Skin, hair and nail conditions.'),
    ('Orthopedics',      'Bones, joints, ligaments and musculoskeletal care.'),
    ('Neurology',        'Brain, spinal cord and nervous system disorders.'),
    ('Pediatrics',       'Medical care for infants, children and adolescents.');

INSERT INTO number_sequences (sequence_name, next_value) VALUES
    ('PATIENT', 1), ('APPOINTMENT', 1), ('PRESCRIPTION', 1), ('LAB_ORDER', 1),
    ('INVOICE', 1), ('PAYMENT', 1), ('DISPENSING', 1), ('EMPLOYEE', 1);

-- Fictional catalog prices for the academic prototype.
INSERT INTO lab_test_catalog (code, name, description, sample_type, price, turnaround_hours) VALUES
    ('CBC',   'Complete Blood Count',      'Measures blood cell counts.',             'Blood', 350.00, 12),
    ('LIPID', 'Lipid Profile',             'Cholesterol and triglyceride levels.',    'Blood', 600.00, 24),
    ('FBS',   'Fasting Blood Sugar',       'Blood glucose after fasting.',            'Blood', 150.00, 6),
    ('HBA1C', 'HbA1c',                     'Average blood glucose (approx. 3 months).', 'Blood', 450.00, 24),
    ('TSH',   'Thyroid Stimulating Hormone', 'Thyroid function screening.',           'Blood', 400.00, 24),
    ('LFT',   'Liver Function Test',       'Liver enzyme and protein panel.',         'Blood', 700.00, 24),
    ('KFT',   'Kidney Function Test',      'Renal function panel.',                   'Blood', 650.00, 24),
    ('URINE', 'Urine Routine Examination', 'Physical, chemical and microscopic urine analysis.', 'Urine', 200.00, 6),
    ('ECG',   'Electrocardiogram',         'Records the electrical activity of the heart.', 'N/A', 300.00, 2),
    ('XRAY-CH', 'Chest X-Ray',             'Radiograph of the chest.',                'N/A',   500.00, 4);
