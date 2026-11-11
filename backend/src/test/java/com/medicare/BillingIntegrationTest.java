package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.medicare.entity.Appointment;
import com.medicare.entity.Consultation;
import com.medicare.entity.DispensingRecord;
import com.medicare.entity.Doctor;
import com.medicare.entity.LabOrder;
import com.medicare.entity.LabTest;
import com.medicare.entity.Medicine;
import com.medicare.entity.MedicineBatch;
import com.medicare.entity.Patient;
import com.medicare.entity.Prescription;
import com.medicare.entity.PrescriptionItem;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.LabPriority;
import com.medicare.enums.MedicineCategory;
import com.medicare.enums.Role;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.DispensingRecordRepository;
import com.medicare.repository.LabOrderRepository;
import com.medicare.repository.LabTestRepository;
import com.medicare.repository.MedicineBatchRepository;
import com.medicare.repository.MedicineRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.PrescriptionRepository;
import com.medicare.service.HospitalClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/** Phase 10: invoices from billable services, payments, refunds, cancellation, access and summary. */
class BillingIntegrationTest extends IntegrationTestBase {

    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired LabTestRepository labTestRepository;
    @Autowired LabOrderRepository labOrderRepository;
    @Autowired MedicineRepository medicineRepository;
    @Autowired MedicineBatchRepository batchRepository;
    @Autowired PrescriptionRepository prescriptionRepository;
    @Autowired DispensingRecordRepository dispensingRepository;
    @Autowired HospitalClock clock;
    @Autowired JdbcTemplate jdbc;

    MockHttpSession desk;
    Patient patient;
    Appointment visit;
    long labItemId;
    long dispensingItemId;

    @BeforeEach
    void setUp() throws Exception {
        desk = loginAs(Role.RECEPTIONIST);
        patient = newPatient("Bina Billpatient");
        Doctor doctor = createDoctor("Dr Bill " + letters(), createDepartment(unique("Bill")), "Physician", "500.00");
        visit = appointmentRepository.save(new Appointment(unique("APT"), patient, doctor, null, clock.today(),
                LocalTime.of(20, 0), LocalTime.of(20, 15), AppointmentType.NEW, "Check-up", patient.getUser().getId()));
        jdbc.update("UPDATE appointments SET status = 'COMPLETED' WHERE id = ?", visit.getId());
        // the fee is recorded at booking: a later fee change must not affect the bill
        jdbc.update("UPDATE doctors SET consultation_fee = 999.00 WHERE id = ?", doctor.getId());
        Consultation c = consultationRepository.save(new Consultation(visit));

        String code = "B" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000));
        LabTest t = new LabTest(code);
        t.update(code, "Billing test " + letters(), null, "Blood", null, null, new BigDecimal("350.00"), 12);
        labTestRepository.save(t);
        LabOrder order = new LabOrder("LAB-T" + code, c, LabPriority.ROUTINE, null);
        order.addItem(t);
        labOrderRepository.saveAndFlush(order);
        labItemId = order.getItems().getFirst().getId();

        Medicine m = new Medicine("Billmed " + letters());
        m.update(m.getName(), null, "Test Labs", MedicineCategory.TABLET, "10 mg", "tablet", new BigDecimal("2.50"), 0);
        medicineRepository.save(m);
        MedicineBatch b = batchRepository.save(new MedicineBatch(m, "BB-1", clock.today().plusDays(100), 50, null, null));
        Prescription rx = new Prescription("RX-B" + code, c);
        rx.replaceItems(List.of(new PrescriptionItem(rx, m.getName(), "10 mg", "1", "daily", "5 days", null, 10, 0)));
        rx.finalizeVersion();
        prescriptionRepository.saveAndFlush(rx);
        User admin = createUser(Role.ADMIN, "Billing Admin");
        DispensingRecord rec = new DispensingRecord("DSP-B" + code, rx, admin, null);
        rec.addItem(rx.getItems().getFirst(), b, 10);
        dispensingRepository.saveAndFlush(rec);
        dispensingItemId = rec.getItems().getFirst().getId();
        // price recorded at dispensing; a later price change must not affect the bill
        jdbc.update("UPDATE medicines SET unit_price = 9.99 WHERE id = ?", m.getId());
    }

    static String letters() {
        return unique("x").replaceAll("[^a-z]", "q");
    }

    Patient newPatient(String name) {
        User u = userRepository.save(new User(uniqueEmail("bp"), passwordHash(), name, "+91 90000 77777", Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("B").toUpperCase().substring(0, 10), LocalDate.of(1992, 2, 2)));
    }

    String invoiceJson(String extra) {
        return "{\"patientId\":" + patient.getId() + ",\"charges\":[{\"type\":\"CONSULTATION\",\"referenceId\":" + visit.getId()
                + "},{\"type\":\"LAB_TEST\",\"referenceId\":" + labItemId + "},{\"type\":\"PHARMACY\",\"referenceId\":" + dispensingItemId + "}]"
                + extra + "}";
    }

    long createInvoice(String json) throws Exception {
        String body = mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    ResultActions money(String kind, long id, String json) throws Exception {
        return mockMvc.perform(post("/api/billing/invoices/{id}/" + kind, id).with(csrf()).session(desk)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void billableChargesUseRecordedPricesAndInvoiceTotalsIncludeDiscountAndTax() throws Exception {
        mockMvc.perform(get("/api/billing/patients/{id}/billable", patient.getId()).session(desk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges.length()").value(3))
                .andExpect(jsonPath("$.charges[?(@.type=='CONSULTATION')].unitPrice", hasItem(500.0)))
                .andExpect(jsonPath("$.charges[?(@.type=='LAB_TEST')].unitPrice", hasItem(350.0)))
                .andExpect(jsonPath("$.charges[?(@.type=='PHARMACY')].lineTotal", hasItem(25.0)));

        BigDecimal oldRate = jdbc.queryForObject("SELECT tax_rate_percent FROM hospital_settings WHERE id = 1", BigDecimal.class);
        jdbc.update("UPDATE hospital_settings SET tax_rate_percent = 5.00 WHERE id = 1");
        try {
            mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                            .content(invoiceJson(",\"discountAmount\":75")))
                    .andExpect(jsonPath("$.code").value("DISCOUNT_REASON_REQUIRED"));
            mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                            .content(invoiceJson(",\"discountAmount\":5000,\"notes\":\"Too much\"")))
                    .andExpect(jsonPath("$.code").value("DISCOUNT_TOO_HIGH"));
            mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                            .content(invoiceJson(",\"otherItems\":[{\"description\":\"Dressing\",\"quantity\":2,\"unitPrice\":50}],"
                                    + "\"discountAmount\":75,\"notes\":\"Senior citizen discount (fictional)\"")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.invoiceNo", matchesPattern("INV-\\d{8}-\\d{6}")))
                    .andExpect(jsonPath("$.status").value("UNPAID"))
                    .andExpect(jsonPath("$.items.length()").value(4))
                    // 500 + 350 + 25 + 100 = 975; -75 = 900; 5% tax = 45; total 945
                    .andExpect(jsonPath("$.subtotal").value(975.0))
                    .andExpect(jsonPath("$.taxAmount").value(45.0))
                    .andExpect(jsonPath("$.totalAmount").value(945.0))
                    .andExpect(jsonPath("$.balanceDue").value(945.0))
                    .andExpect(jsonPath("$.canPay").value(true));
        } finally {
            jdbc.update("UPDATE hospital_settings SET tax_rate_percent = ? WHERE id = 1", oldRate);
        }
        mockMvc.perform(get("/api/billing/patients/{id}/billable", patient.getId()).session(desk))
                .andExpect(jsonPath("$.charges.length()").value(0))
                .andExpect(jsonPath("$.outstandingBalance").value(945.0));
        mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_BILLED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications n JOIN patients p ON p.user_id = n.user_id "
                + "WHERE p.id = ? AND n.type = 'INVOICE_GENERATED'", Integer.class, patient.getId())).isEqualTo(1);
    }

    @Test
    void chargesOfOtherPatientsOrUnvisitedAppointmentsCannotBeBilled() throws Exception {
        Patient other = newPatient("Omar Otherbill");
        mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":" + other.getId() + ",\"charges\":[{\"type\":\"CONSULTATION\",\"referenceId\":" + visit.getId() + "}]}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CHARGE_NOT_BILLABLE"));
        jdbc.update("UPDATE appointments SET status = 'SCHEDULED' WHERE id = ?", visit.getId());
        mockMvc.perform(get("/api/billing/patients/{id}/billable", patient.getId()).session(desk))
                .andExpect(jsonPath("$.charges[*].type", not(hasItem("CONSULTATION"))));
        mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":" + patient.getId() + "}"))
                .andExpect(jsonPath("$.code").value("EMPTY_INVOICE"));
    }

    @Test
    void paymentsRefundsAndCancellationFollowTheRules() throws Exception {
        long id = createInvoice(invoiceJson(""));   // 875.00, tax rate 0 by default
        BigDecimal total = jdbc.queryForObject("SELECT total_amount FROM invoices WHERE id = ?", BigDecimal.class, id);
        assertThat(total).isEqualByComparingTo("875.00");

        money("payments", id, "{\"method\":\"CASH\",\"amount\":900}").andExpect(jsonPath("$.code").value("AMOUNT_EXCEEDS_BALANCE"));
        money("payments", id, "{\"method\":\"UPI_SIMULATED\",\"amount\":100}").andExpect(jsonPath("$.code").value("REFERENCE_REQUIRED"));
        money("payments", id, "{\"method\":\"CARD_SIMULATED\",\"amount\":100,\"transactionReference\":\"4111 1111-1111 1111\"}")
                .andExpect(jsonPath("$.code").value("CARD_NUMBER_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE invoice_id = ?", Integer.class, id)).isZero();

        money("payments", id, "{\"method\":\"CASH\",\"amount\":300}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balanceDue").value(575.0))
                .andExpect(jsonPath("$.canCancel").value(false));
        mockMvc.perform(post("/api/billing/invoices/{id}/cancel", id).with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Wrong patient\"}")).andExpect(jsonPath("$.code").value("INVOICE_NOT_CANCELLABLE"));
        money("payments", id, "{\"method\":\"UPI_SIMULATED\",\"amount\":575,\"transactionReference\":\"UPI-123456789012\"}")
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payments[1].paymentNo", matchesPattern("PAY-\\d{8}-\\d{6}")));
        money("payments", id, "{\"method\":\"CASH\",\"amount\":1}").andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));

        money("refunds", id, "{\"method\":\"CASH\",\"amount\":100}").andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        money("refunds", id, "{\"method\":\"CASH\",\"amount\":900,\"notes\":\"Too much\"}").andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_PAID"));
        money("refunds", id, "{\"method\":\"CASH\",\"amount\":75,\"notes\":\"Lab test not done (fictional)\"}")
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(800.0))
                .andExpect(jsonPath("$.payments[2].paymentNo", matchesPattern("RFD-\\d{8}-\\d{6}")));
        money("refunds", id, "{\"method\":\"CASH\",\"amount\":800,\"notes\":\"Visit disputed (fictional)\"}")
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.amountPaid").value(0.0));

        // cancelling an unpaid invoice releases its charges
        long second = createInvoice("{\"patientId\":" + patient.getId() + ",\"otherItems\":[{\"description\":\"Registration\",\"quantity\":1,\"unitPrice\":20}]}");
        Patient p2 = newPatient("Cara Cancelbill");
        jdbc.update("UPDATE invoices SET patient_id = ? WHERE id = ?", p2.getId(), second);  // isolate from the charges above
        mockMvc.perform(post("/api/billing/invoices/{id}/cancel", second).with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Created by mistake\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingFreesTheChargesForANewInvoice() throws Exception {
        long id = createInvoice(invoiceJson(""));
        mockMvc.perform(post("/api/billing/invoices/{id}/cancel", id).with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Patient asked for separate bills\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/billing/patients/{id}/billable", patient.getId()).session(desk))
                .andExpect(jsonPath("$.charges.length()").value(3));
        createInvoice(invoiceJson(""));
    }

    @Test
    void accessRulesForPatientsDoctorsAndAdmins() throws Exception {
        long id = createInvoice(invoiceJson(""));
        MockHttpSession own = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/patients/me/invoices").session(own)).andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(get("/api/billing/invoices/{id}", id).session(own))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canPay").value(false));
        String pdf = mockMvc.perform(get("/api/billing/invoices/{id}/pdf", id).session(own))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(pdf).startsWith("%PDF");

        MockHttpSession other = login(newPatient("Otto Otherpatient").getUser().getEmail());
        mockMvc.perform(get("/api/billing/invoices/{id}", id).session(other)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/billing/invoices/{id}/pdf", id).session(other)).andExpect(status().isNotFound());

        MockHttpSession doctor = loginAs(Role.DOCTOR);
        mockMvc.perform(get("/api/billing/invoices/{id}", id).session(doctor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/billing/patients/{pid}/billable", patient.getId()).session(doctor)).andExpect(status().isForbidden());

        MockHttpSession admin = loginAs(Role.ADMIN);
        mockMvc.perform(get("/api/billing/invoices/{id}", id).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canPay").value(false));
        mockMvc.perform(post("/api/billing/invoices/{id}/payments", id).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"CASH\",\"amount\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/billing/summary").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceCount").isNumber())
                .andExpect(jsonPath("$.outstandingTotal").isNumber());
        mockMvc.perform(get("/api/admin/billing/summary").session(desk)).andExpect(status().isForbidden());
    }

    @Test
    void simultaneousPaymentsNeverOverpay() throws Exception {
        long id = createInvoice(invoiceJson(""));
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return money("payments", id, "{\"method\":\"CASH\",\"amount\":875}").andReturn().getResponse().getStatus();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(threads - 1);
        assertThat(jdbc.queryForObject("SELECT amount_paid FROM invoices WHERE id = ?", BigDecimal.class, id)).isEqualByComparingTo("875.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE invoice_id = ?", Integer.class, id)).isEqualTo(1);
    }
}
