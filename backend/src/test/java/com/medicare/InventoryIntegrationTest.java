package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.medicare.entity.Doctor;
import com.medicare.entity.Medicine;
import com.medicare.entity.MedicineBatch;
import com.medicare.entity.Patient;
import com.medicare.entity.Prescription;
import com.medicare.entity.PrescriptionItem;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.MedicineCategory;
import com.medicare.enums.Role;
import com.medicare.notification.InventoryAlertJob;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.ConsultationRepository;
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

/** Phase 9: medicine catalog, stock ledger, alerts and dispensing against finalized prescriptions. */
class InventoryIntegrationTest extends IntegrationTestBase {

    @Autowired MedicineRepository medicineRepository;
    @Autowired MedicineBatchRepository batchRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionRepository prescriptionRepository;
    @Autowired InventoryAlertJob alertJob;
    @Autowired HospitalClock clock;
    @Autowired JdbcTemplate jdbc;

    MockHttpSession admin;
    String name;
    LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAs(Role.ADMIN);
        name = "Zx" + unique("m").replaceAll("[^a-z]", "q");
        today = clock.today();
    }

    // ------------------------------------------------------------------ helpers

    String medicineJson(String n, String strength, int reorder) {
        return """
                {"name":"%s","genericName":"%s generic","manufacturer":"Test Labs (fictional)","category":"TABLET",
                 "strength":"%s","unit":"tablet","unitPrice":2.50,"reorderLevel":%d}""".formatted(n, n, strength, reorder);
    }

    long createMedicine(String n, String strength, int reorder) throws Exception {
        var response = mockMvc.perform(post("/api/admin/inventory/medicines").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(medicineJson(n, strength, reorder)))
                .andReturn().getResponse();
        String json = response.getContentAsString();
        assertThat(response.getStatus()).as("create %s %s -> %s", n, strength, json).isEqualTo(201);
        return Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    ResultActions stockIn(long medicineId, String batch, LocalDate expiry, int qty) throws Exception {
        return mockMvc.perform(post("/api/admin/inventory/medicines/{id}/batches", medicineId).with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchNumber\":\"" + batch + "\",\"expiryDate\":\"" + expiry + "\",\"quantity\":" + qty
                        + ",\"purchasePrice\":1.10,\"supplier\":\"Test Supplier\"}"));
    }

    long batchId(long medicineId, String batch) {
        return batchRepository.findByMedicineIdOrderByExpiryDateAscIdAsc(medicineId).stream()
                .filter(b -> b.getBatchNumber().equals(batch)).findFirst().orElseThrow().getId();
    }

    int available(long batchId) {
        return batchRepository.findById(batchId).orElseThrow().getQuantityAvailable();
    }

    /** An expired batch can only exist in the database (stock-in refuses past expiry dates). */
    long expiredBatch(long medicineId, int qty) {
        Medicine m = medicineRepository.findById(medicineId).orElseThrow();
        return batchRepository.save(new MedicineBatch(m, "OLD-" + unique("b").substring(0, 6), today.minusDays(3), qty, null, null)).getId();
    }

    /** A finalized prescription with the given lines: {medicineName, strength, quantity-or-null}. */
    Prescription finalizedRx(Object[]... lines) {
        Doctor doctor = createDoctor("Dr Inv " + unique("d").replaceAll("[^a-z]", "q"), createDepartment(unique("Inv")), "Physician", "300");
        User u = userRepository.save(new User(uniqueEmail("ip"), passwordHash(), "Indu Invpatient", "+91 90000 66666", Role.PATIENT));
        Patient patient = patientRepository.save(new Patient(u, unique("I").toUpperCase().substring(0, 10), LocalDate.of(1990, 1, 1)));
        Appointment a = appointmentRepository.save(new Appointment(unique("APT"), patient, doctor, null, today,
                LocalTime.of(21, 0), LocalTime.of(21, 15), AppointmentType.NEW, "Cough", u.getId()));
        Consultation c = consultationRepository.save(new Consultation(a));
        Prescription p = new Prescription("RX-T" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000)), c);
        List<PrescriptionItem> items = new ArrayList<>();
        int order = 0;
        for (Object[] l : lines) {
            items.add(new PrescriptionItem(p, (String) l[0], (String) l[1], "1 tablet", "Twice daily", "5 days", null,
                    (Integer) l[2], order++));
        }
        p.replaceItems(items);
        p.finalizeVersion();
        return prescriptionRepository.saveAndFlush(p);
    }

    ResultActions dispense(MockHttpSession s, long rxId, long itemId, long batchId, int qty) throws Exception {
        return mockMvc.perform(post("/api/admin/dispensing").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"prescriptionId\":" + rxId + ",\"notes\":\"Counter 1\",\"lines\":[{\"prescriptionItemId\":" + itemId
                        + ",\"batchId\":" + batchId + ",\"quantity\":" + qty + "}]}"));
    }

    // ------------------------------------------------------------------ catalog and stock

    @Test
    void catalogStockLedgerAndRules() throws Exception {
        long id = createMedicine(name, "500 mg", 50);
        mockMvc.perform(post("/api/admin/inventory/medicines").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(medicineJson(name, "500 mg", 50))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEDICINE_EXISTS"));
        mockMvc.perform(post("/api/admin/inventory/medicines").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(medicineJson(name, "500 mg", 50).replace("\"TABLET\"", "\"POTION\""))).andExpect(status().isBadRequest());

        stockIn(id, "B-1", today, 10).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BATCH_EXPIRED"));
        stockIn(id, "B-1", today.plusDays(20), 30).andExpect(status().isCreated())
                .andExpect(jsonPath("$.medicine.availableQuantity").value(30))
                .andExpect(jsonPath("$.medicine.lowStock").value(true))
                .andExpect(jsonPath("$.batches[0].expiringSoon").value(true));
        stockIn(id, "b-1", today.plusDays(90), 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATCH_EXISTS"));
        stockIn(id, "B-2", today.plusDays(400), 40).andExpect(status().isCreated())
                .andExpect(jsonPath("$.medicine.availableQuantity").value(70))
                .andExpect(jsonPath("$.medicine.lowStock").value(false))
                .andExpect(jsonPath("$.medicine.nearestExpiry").value(today.plusDays(20).toString()));

        long b1 = batchId(id, "B-1");
        mockMvc.perform(post("/api/admin/inventory/batches/{b}/adjustments", b1).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ADJUSTMENT_OUT\",\"quantity\":31,\"reason\":\"Count correction\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        mockMvc.perform(post("/api/admin/inventory/batches/{b}/adjustments", b1).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DISPENSE\",\"quantity\":1,\"reason\":\"Sneaky\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_ADJUSTMENT"));
        mockMvc.perform(post("/api/admin/inventory/batches/{b}/adjustments", b1).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ADJUSTMENT_OUT\",\"quantity\":4,\"reason\":\"Damaged strip\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.medicine.availableQuantity").value(66));
        mockMvc.perform(post("/api/admin/inventory/batches/{b}/write-off", b1).with(csrf()).session(admin))
                .andExpect(jsonPath("$.code").value("BATCH_NOT_EXPIRED"));

        long old = expiredBatch(id, 7);
        mockMvc.perform(post("/api/admin/inventory/batches/{b}/adjustments", old).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ADJUSTMENT_IN\",\"quantity\":1,\"reason\":\"Found one\"}"))
                .andExpect(jsonPath("$.code").value("BATCH_EXPIRED"));
        mockMvc.perform(get("/api/admin/inventory/medicines/{id}", id).session(admin))
                .andExpect(jsonPath("$.medicine.availableQuantity").value(66))
                .andExpect(jsonPath("$.medicine.expiredQuantity").value(7));
        mockMvc.perform(post("/api/admin/inventory/batches/{b}/write-off", old).with(csrf()).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.medicine.expiredQuantity").value(0));

        // ledger: newest first, balances recorded
        mockMvc.perform(get("/api/admin/inventory/transactions").param("medicineId", String.valueOf(id)).session(admin))
                .andExpect(jsonPath("$.content[0].type").value("EXPIRED_WRITE_OFF"))
                .andExpect(jsonPath("$.content[0].quantity").value(7))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(0))
                .andExpect(jsonPath("$.content[1].type").value("ADJUSTMENT_OUT"))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(26))
                .andExpect(jsonPath("$.content[1].notes").value("Damaged strip"))
                .andExpect(jsonPath("$.totalElements").value(4));

        // deactivated medicines cannot receive stock
        mockMvc.perform(patch("/api/admin/inventory/medicines/{id}/status", id).with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        stockIn(id, "B-3", today.plusDays(100), 1).andExpect(jsonPath("$.code").value("MEDICINE_INACTIVE"));

        for (Role role : new Role[]{Role.DOCTOR, Role.RECEPTIONIST, Role.PATIENT}) {
            MockHttpSession s = loginAs(role);
            mockMvc.perform(get("/api/admin/inventory/medicines").session(s)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/dispensing/prescriptions/RX-1").session(s)).andExpect(status().isForbidden());
        }
    }

    @Test
    void alertsListLowStockExpiringAndExpiredAndTheDailyJobNotifiesOncePerDay() throws Exception {
        long low = createMedicine(name, "10 mg", 100);
        stockIn(low, "L-1", today.plusDays(10), 5).andExpect(status().isCreated());
        long old = expiredBatch(low, 3);
        mockMvc.perform(get("/api/admin/inventory/alerts").session(admin))
                .andExpect(jsonPath("$.lowStock[*].id", hasItem((int) low)))
                .andExpect(jsonPath("$.expiringSoon[*].batchNumber", hasItem("L-1")))
                .andExpect(jsonPath("$.expired[*].batchId", hasItem((int) old)));

        alertJob.runDailyAlerts();
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE idempotency_key LIKE ?", Integer.class,
                "INV_ALERT:" + today + ":%");
        assertThat(before).isPositive();
        assertThat(alertJob.runDailyAlerts()).isZero();
    }

    // ------------------------------------------------------------------ dispensing

    @Test
    void dispensingFollowsThePrescriptionAndStock() throws Exception {
        long med = createMedicine(name, "500 mg", 5);
        long other = createMedicine(name, "650 mg", 5);
        stockIn(med, "D-1", today.plusDays(30), 4).andExpect(status().isCreated());
        stockIn(med, "D-2", today.plusDays(300), 50).andExpect(status().isCreated());
        stockIn(other, "O-1", today.plusDays(300), 50).andExpect(status().isCreated());
        long d1 = batchId(med, "D-1");
        long d2 = batchId(med, "D-2");
        long o1 = batchId(other, "O-1");
        long expired = expiredBatch(med, 10);

        Prescription rx = finalizedRx(new Object[]{name + " (fictional)", "500mg", 10}, new Object[]{name + " syrup", null, null});
        long line1 = rx.getItems().get(0).getId();
        long line2 = rx.getItems().get(1).getId();

        mockMvc.perform(get("/api/admin/dispensing/prescriptions/{no}", rx.getPrescriptionNo().toLowerCase()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("Indu Invpatient"))
                .andExpect(jsonPath("$.items[0].remainingQuantity").value(10))
                .andExpect(jsonPath("$.items[0].candidates.length()").value(1))
                .andExpect(jsonPath("$.items[0].candidates[0].medicineId").value(med))
                .andExpect(jsonPath("$.items[0].candidates[0].batches[0].batchNumber").value("D-1"))
                .andExpect(jsonPath("$.items[0].candidates[0].batches.length()").value(2))
                .andExpect(jsonPath("$.items[1].remainingQuantity").doesNotExist())
                .andExpect(jsonPath("$.items[1].candidates.length()").value(2))
                .andExpect(jsonPath("$.diagnosis").doesNotExist());

        dispense(admin, rx.getId(), line1, o1, 1).andExpect(jsonPath("$.code").value("MEDICINE_MISMATCH"));
        dispense(admin, rx.getId(), line1, expired, 1).andExpect(jsonPath("$.code").value("BATCH_EXPIRED"));
        dispense(admin, rx.getId(), line1, d1, 5).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        dispense(admin, rx.getId(), line1, d2, 11).andExpect(jsonPath("$.code").value("QUANTITY_EXCEEDS_PRESCRIPTION"));
        assertThat(available(d1)).isEqualTo(4);

        // split one line over two batches in one dispensing
        mockMvc.perform(post("/api/admin/dispensing").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prescriptionId\":" + rx.getId() + ",\"lines\":[{\"prescriptionItemId\":" + line1 + ",\"batchId\":" + d1
                                + ",\"quantity\":4},{\"prescriptionItemId\":" + line1 + ",\"batchId\":" + d2 + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dispensingNo", matchesPattern("DSP-\\d{8}-\\d{6}")))
                .andExpect(jsonPath("$.items.length()").value(2));
        assertThat(available(d1)).isZero();
        assertThat(available(d2)).isEqualTo(48);
        dispense(admin, rx.getId(), line1, d2, 5).andExpect(jsonPath("$.code").value("QUANTITY_EXCEEDS_PRESCRIPTION"));
        dispense(admin, rx.getId(), line1, d2, 4).andExpect(status().isCreated());
        dispense(admin, rx.getId(), line1, d2, 1).andExpect(jsonPath("$.code").value("ALREADY_DISPENSED"));

        // a line without a prescribed quantity is dispensed once
        dispense(admin, rx.getId(), line2, o1, 1).andExpect(status().isCreated());
        dispense(admin, rx.getId(), line2, o1, 1).andExpect(jsonPath("$.code").value("ALREADY_DISPENSED"));

        mockMvc.perform(get("/api/admin/dispensing/prescriptions/{no}", rx.getPrescriptionNo()).session(admin))
                .andExpect(jsonPath("$.items[0].fullyDispensed").value(true))
                .andExpect(jsonPath("$.items[0].dispensedQuantity").value(10))
                .andExpect(jsonPath("$.previousDispensings.length()").value(3));
        mockMvc.perform(get("/api/admin/inventory/transactions").param("medicineId", String.valueOf(med)).param("type", "DISPENSE").session(admin))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].reference", matchesPattern("DSP-.*")));
        mockMvc.perform(get("/api/admin/dispensing").param("search", rx.getPrescriptionNo()).session(admin))
                .andExpect(jsonPath("$.totalElements").value(3));
        Integer audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'PRESCRIPTION_VIEWED_FOR_DISPENSING' AND entity_id = ?",
                Integer.class, String.valueOf(rx.getId()));
        assertThat(audits).isEqualTo(2);

        // drafts cannot be looked up or dispensed
        Prescription draft = finalizedRx(new Object[]{name, "500 mg", 1});
        jdbc.update("UPDATE prescriptions SET status = 'DRAFT' WHERE id = ?", draft.getId());
        mockMvc.perform(get("/api/admin/dispensing/prescriptions/{no}", draft.getPrescriptionNo()).session(admin)).andExpect(status().isNotFound());
        dispense(admin, draft.getId(), draft.getItems().get(0).getId(), d2, 1).andExpect(jsonPath("$.code").value("PRESCRIPTION_NOT_FINALIZED"));
    }

    @Test
    void simultaneousDispensingNeverOversellsABatch() throws Exception {
        long med = createMedicine(name, "250 mg", 0);
        stockIn(med, "C-1", today.plusDays(200), 5).andExpect(status().isCreated());
        long batch = batchId(med, "C-1");
        int threads = 4;
        List<Prescription> rxs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            rxs.add(finalizedRx(new Object[]{name, "250 mg", 5}));
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Prescription rx : rxs) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return dispense(admin, rx.getId(), rx.getItems().get(0).getId(), batch, 5).andReturn().getResponse().getStatus();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(threads - 1);
        assertThat(available(batch)).isZero();
        Integer ledger = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transactions WHERE batch_id = ? AND txn_type = 'DISPENSE'",
                Integer.class, batch);
        assertThat(ledger).isEqualTo(1);
    }

    @Test
    void matcherIgnoresCaseSpacesAndPunctuation() {
        Medicine m = new Medicine("Paracetamol");
        m.update("Paracetamol", "Acetaminophen", "X", MedicineCategory.TABLET, "500 mg", "tablet", BigDecimal.ONE, 1);
        assertThat(com.medicare.service.MedicineMatcher.matches("PARACETAMOL (fictional)", "500MG", m)).isTrue();
        assertThat(com.medicare.service.MedicineMatcher.matches("acetaminophen", null, m)).isTrue();
        assertThat(com.medicare.service.MedicineMatcher.matches("Paracetamol", "650 mg", m)).isFalse();
        assertThat(com.medicare.service.MedicineMatcher.matches("Ibuprofen", "500 mg", m)).isFalse();
    }
}
