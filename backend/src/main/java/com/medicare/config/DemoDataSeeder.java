package com.medicare.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import com.medicare.entity.Department;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.entity.Receptionist;
import com.medicare.entity.User;
import com.medicare.enums.BloodGroup;
import com.medicare.enums.Gender;
import com.medicare.enums.Role;
import com.medicare.repository.DepartmentRepository;
import com.medicare.repository.DoctorAvailabilityRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.ReceptionistRepository;
import com.medicare.repository.UserRepository;
import com.medicare.service.SequenceService;
import com.medicare.validation.StrongPasswordValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates FICTIONAL demo accounts for every role (admin, receptionist, doctors, patients). Runs only with the {@code demo}
 * Spring profile, refuses to run together with {@code prod}, and is idempotent
 * (existing e-mails are skipped), so it is safe to restart.
 *
 * <p>All demo users share the password from {@code DEMO_PASSWORD}.</p>
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ReceptionistRepository receptionistRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final SequenceService sequenceService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String demoPassword;

    private final com.medicare.repository.MedicineRepository medicineRepository;
    private final com.medicare.repository.MedicineBatchRepository batchRepository;
    private final com.medicare.repository.InventoryTransactionRepository transactionRepository;
    private final com.medicare.service.HospitalClock clock;

    public DemoDataSeeder(UserRepository userRepository, PatientRepository patientRepository,
                          DoctorRepository doctorRepository, ReceptionistRepository receptionistRepository,
                          DepartmentRepository departmentRepository,
                          DoctorAvailabilityRepository availabilityRepository, SequenceService sequenceService,
                          PasswordEncoder passwordEncoder, Environment environment,
                          @Value("${medicare.demo.password:}") String demoPassword,
                          com.medicare.repository.MedicineRepository medicineRepository,
                          com.medicare.repository.MedicineBatchRepository batchRepository,
                          com.medicare.repository.InventoryTransactionRepository transactionRepository,
                          com.medicare.service.HospitalClock clock) {
        this.medicineRepository = medicineRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.receptionistRepository = receptionistRepository;
        this.departmentRepository = departmentRepository;
        this.availabilityRepository = availabilityRepository;
        this.sequenceService = sequenceService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("The demo profile must never be combined with the prod profile");
        }
        if (demoPassword == null || !StrongPasswordValidator.isStrong(demoPassword)) {
            throw new IllegalStateException("DEMO_PASSWORD must be set to a strong password "
                    + "(8+ chars, upper, lower, digit, special) when the demo profile is active");
        }
        String hash = passwordEncoder.encode(demoPassword);
        int created = 0;

        if (createUser("admin@medicare.example", "Asha Administrator", "+91 90000 00001", Role.ADMIN, hash) != null) {
            created++;
        }
        User rec = createUser("reception@medicare.example", "Rohan Desk", "+91 90000 00002", Role.RECEPTIONIST, hash);
        if (rec != null) {
            receptionistRepository.save(new Receptionist(rec, sequenceService.nextEmployeeCode()));
            created++;
        }

        created += createDoctor("dr.mehta@medicare.example", "Dr. Kavya Mehta", "Cardiology", "Cardiologist",
                "MBBS, MD (Medicine), DM (Cardiology)", "DEMO-LIC-1001", 12, "800.00", 20, hash);
        created += createDoctor("dr.rao@medicare.example", "Dr. Arjun Rao", "General Medicine", "General Physician",
                "MBBS, MD (General Medicine)", "DEMO-LIC-1002", 8, "400.00", 15, hash);
        created += createDoctor("dr.khan@medicare.example", "Dr. Sara Khan", "Dermatology", "Dermatologist",
                "MBBS, MD (Dermatology)", "DEMO-LIC-1003", 6, "600.00", 15, hash);
        created += createDoctor("dr.iyer@medicare.example", "Dr. Vikram Iyer", "Orthopedics", "Orthopedic Surgeon",
                "MBBS, MS (Orthopedics)", "DEMO-LIC-1004", 15, "700.00", 20, hash);
        created += createDoctor("dr.nair@medicare.example", "Dr. Meera Nair", "Cardiology", "Cardiologist",
                "MBBS, MD, DNB (Cardiology)", "DEMO-LIC-1005", 9, "750.00", 20, hash);

        created += createPatient("ravi.patient@medicare.example", "Ravi Kumar", "+91 90000 10001",
                LocalDate.of(1988, 4, 12), Gender.MALE, BloodGroup.B_POS, "Flat 4, Example Residency, Sample City",
                "Sunita Kumar", "+91 90000 10011", hash);
        created += createPatient("anita.patient@medicare.example", "Anita Sharma", "+91 90000 10002",
                LocalDate.of(1995, 9, 30), Gender.FEMALE, BloodGroup.O_POS, "22 Demo Street, Sample City",
                "Raj Sharma", "+91 90000 10012", hash);

        int schedules = seedSchedules();
        int medicines = seedInventory();
        log.info("Demo data: {} account(s) created, {} doctor schedule(s) and {} medicine(s) added (existing data left unchanged)",
                created, schedules, medicines);
    }

    /**
     * Fictional medicines with batches chosen so that every inventory alert is visible: one medicine below its
     * reorder level, one batch expiring soon and one expired batch. Runs only on an empty catalog.
     */
    private int seedInventory() {
        if (medicineRepository.count() > 0) {
            return 0;
        }
        User admin = userRepository.findByEmailIgnoreCase("admin@medicare.example").orElse(null);
        if (admin == null) {
            return 0;
        }
        LocalDate today = clock.today();
        Object[][] data = {
                // name, generic, category, strength, unit, price, reorder, {batch, days to expiry, qty}...
                {"Paracetamol", "Paracetamol", "TABLET", "500 mg", "tablet", "2.00", 50,
                        new Object[][]{{"DEMO-PCM-01", 30, 40}, {"DEMO-PCM-02", 400, 200}}},
                {"Cetirizine", "Cetirizine", "TABLET", "10 mg", "tablet", "3.50", 30, new Object[][]{{"DEMO-CTZ-01", 300, 20}}},
                {"Amoxicillin", "Amoxicillin", "CAPSULE", "250 mg", "capsule", "6.00", 40,
                        new Object[][]{{"DEMO-AMX-00", -10, 15}, {"DEMO-AMX-01", 200, 120}}},
                {"Salbutamol Inhaler", "Salbutamol", "INHALER", "100 mcg", "inhaler", "180.00", 5, new Object[][]{{"DEMO-SAL-01", 500, 12}}},
                {"Dextromethorphan Syrup", "Dextromethorphan", "SYRUP", "10 mg/5 mL", "bottle", "95.00", 10,
                        new Object[][]{{"DEMO-DXM-01", 250, 25}}},
        };
        for (Object[] row : data) {
            var m = new com.medicare.entity.Medicine((String) row[0]);
            m.update((String) row[0], (String) row[1], "Demo Pharma Ltd (fictional)",
                    com.medicare.enums.MedicineCategory.valueOf((String) row[2]), (String) row[3], (String) row[4],
                    new java.math.BigDecimal((String) row[5]), (Integer) row[6]);
            medicineRepository.save(m);
            for (Object[] b : (Object[][]) row[7]) {
                var batch = batchRepository.save(new com.medicare.entity.MedicineBatch(m, (String) b[0],
                        today.plusDays((Integer) b[1]), (Integer) b[2], null, "Demo Supplier (fictional)"));
                transactionRepository.save(new com.medicare.entity.InventoryTransaction(batch,
                        com.medicare.enums.InventoryTxnType.STOCK_IN, (Integer) b[2], (Integer) b[2], null, "DEMO-SEED",
                        "Demo opening stock", admin));
            }
        }
        return data.length;
    }

    /** Mon–Sat 09:00–17:00 with a 13:00–14:00 break for every demo doctor that has no schedule yet. */
    private int seedSchedules() {
        int count = 0;
        for (String email : new String[]{"dr.mehta@medicare.example", "dr.rao@medicare.example", "dr.khan@medicare.example",
                "dr.iyer@medicare.example", "dr.nair@medicare.example"}) {
            var doctor = userRepository.findByEmailIgnoreCase(email).flatMap(u -> doctorRepository.findByUserId(u.getId())).orElse(null);
            if (doctor == null || !availabilityRepository.findByDoctorIdOrderByDayOfWeek(doctor.getId()).isEmpty()) {
                continue;
            }
            for (int day = 1; day <= 6; day++) {
                DoctorAvailability a = new DoctorAvailability(doctor.getId(), day);
                a.update(LocalTime.of(9, 0), LocalTime.of(17, 0), LocalTime.of(13, 0), LocalTime.of(14, 0), true);
                availabilityRepository.save(a);
            }
            count++;
        }
        return count;
    }

    private User createUser(String email, String name, String phone, Role role, String hash) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return null;
        }
        return userRepository.save(new User(email, hash, name, phone, role));
    }

    private int createDoctor(String email, String name, String departmentName, String specialization,
                             String qualifications, String license, int experience, String fee, int slotMinutes,
                             String hash) {
        User user = createUser(email, name, "+91 90000 2" + license.substring(license.length() - 4), Role.DOCTOR, hash);
        if (user == null) {
            return 0;
        }
        Department dept = departmentRepository.findByNameIgnoreCase(departmentName)
                .orElseThrow(() -> new IllegalStateException("Department missing: " + departmentName));
        Doctor doctor = new Doctor(user, dept, specialization, qualifications, license, experience,
                new BigDecimal(fee), slotMinutes);
        doctor.setBio(name + " is a fictional " + specialization.toLowerCase()
                + " created for the MediCare academic demonstration.");
        doctorRepository.save(doctor);
        return 1;
    }

    private int createPatient(String email, String name, String phone, LocalDate dob, Gender gender, BloodGroup blood,
                              String address, String emergencyName, String emergencyPhone, String hash) {
        User user = createUser(email, name, phone, Role.PATIENT, hash);
        if (user == null) {
            return 0;
        }
        Patient p = new Patient(user, sequenceService.nextPatientCode(), dob);
        p.setGender(gender);
        p.setBloodGroup(blood);
        p.setAddress(address);
        p.setEmergencyContactName(emergencyName);
        p.setEmergencyContactPhone(emergencyPhone);
        patientRepository.save(p);
        return 1;
    }
}
