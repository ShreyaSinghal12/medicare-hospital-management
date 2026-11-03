package com.medicare.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import com.medicare.entity.Department;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.Doctor;
import com.medicare.entity.LabTechnician;
import com.medicare.entity.Patient;
import com.medicare.entity.Pharmacist;
import com.medicare.entity.Receptionist;
import com.medicare.entity.User;
import com.medicare.enums.BloodGroup;
import com.medicare.enums.Gender;
import com.medicare.enums.Role;
import com.medicare.repository.DepartmentRepository;
import com.medicare.repository.DoctorAvailabilityRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.LabTechnicianRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.PharmacistRepository;
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
 * Creates FICTIONAL demo accounts for every role. Runs only with the {@code demo}
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
    private final LabTechnicianRepository labTechnicianRepository;
    private final PharmacistRepository pharmacistRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final SequenceService sequenceService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String demoPassword;

    public DemoDataSeeder(UserRepository userRepository, PatientRepository patientRepository,
                          DoctorRepository doctorRepository, ReceptionistRepository receptionistRepository,
                          LabTechnicianRepository labTechnicianRepository, PharmacistRepository pharmacistRepository,
                          DepartmentRepository departmentRepository,
                          DoctorAvailabilityRepository availabilityRepository, SequenceService sequenceService,
                          PasswordEncoder passwordEncoder, Environment environment,
                          @Value("${medicare.demo.password:}") String demoPassword) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.receptionistRepository = receptionistRepository;
        this.labTechnicianRepository = labTechnicianRepository;
        this.pharmacistRepository = pharmacistRepository;
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
        User lab = createUser("lab@medicare.example", "Lata Labwell", "+91 90000 00003", Role.LAB_TECHNICIAN, hash);
        if (lab != null) {
            labTechnicianRepository.save(new LabTechnician(lab, sequenceService.nextEmployeeCode(), "B.Sc. MLT"));
            created++;
        }
        User ph = createUser("pharmacy@medicare.example", "Paresh Pillbox", "+91 90000 00004", Role.PHARMACIST, hash);
        if (ph != null) {
            pharmacistRepository.save(new Pharmacist(ph, sequenceService.nextEmployeeCode(), "PH-DEMO-001"));
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
        log.info("Demo data: {} account(s) created, {} doctor schedule(s) added (existing data left unchanged)",
                created, schedules);
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
