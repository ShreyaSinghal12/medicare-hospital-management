package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.Role;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.PasswordResetTokenRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Phase 3: doctor directory, departments, admin doctor/staff/patient management, doctor self-profile. */
class DirectoryAndAdminIntegrationTest extends IntegrationTestBase {

    @Autowired PatientRepository patientRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;

    static String doctorJson(String name, String email, Long deptId, String license, String fee) {
        return """
                {"fullName":"%s","email":"%s","phone":"+91 98888 77777","departmentId":%d,
                 "specialization":"Cardiologist","qualifications":"MBBS, MD","licenseNumber":"%s",
                 "experienceYears":7,"consultationFee":%s,"slotDurationMinutes":20,"bio":"Fictional doctor"}
                """.formatted(name, email, deptId, license, fee);
    }

    // ------------------------------------------------------------- public directory

    @Test
    void publicDirectorySearchesAndFiltersActiveDoctorsOnly() throws Exception {
        Department dept = createDepartment(unique("Dept"));
        String tag = unique("Zed").replace("-", "");
        Doctor active = createDoctor("Dr " + tag + " Alpha", dept, "Cardiologist", "500.00");
        Doctor inactive = createDoctor("Dr " + tag + " Beta", dept, "Cardiologist", "500.00");
        inactive.getUser().setActive(false);
        userRepository.save(inactive.getUser());

        mockMvc.perform(get("/api/doctors").param("search", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId()))
                .andExpect(jsonPath("$.content[0].departmentName").value(dept.getName()))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].licenseNumber").doesNotExist());

        mockMvc.perform(get("/api/doctors").param("departmentId", dept.getId().toString())
                        .param("specialization", "cardiologist"))
                .andExpect(jsonPath("$.content[*].id", hasItem(active.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(inactive.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].departmentId", everyItem(org.hamcrest.Matchers.is(dept.getId().intValue()))));

        mockMvc.perform(get("/api/doctors/{id}", active.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationFee").value(500.00));
        mockMvc.perform(get("/api/doctors/{id}", inactive.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/doctors/specializations"))
                .andExpect(jsonPath("$", hasItem("Cardiologist")));
    }

    @Test
    void doctorsOfInactiveDepartmentsAreHiddenFromThePublic() throws Exception {
        Department dept = createDepartment(unique("Closed"));
        Doctor doc = createDoctor("Dr Hidden " + unique("x").replace("-", ""), dept, "Neurologist", "300.00");
        dept.setActive(false);
        departmentRepository.save(dept);
        mockMvc.perform(get("/api/doctors/{id}", doc.getId())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- departments

    @Test
    void adminManagesDepartmentsWithUniquenessAndDeactivationRules() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String name = unique("Oncology");

        String body = mockMvc.perform(post("/api/admin/departments").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\",\"description\":\"Cancer care\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/admin/departments").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_EXISTS"));

        Department dept = departmentRepository.findById(id).orElseThrow();
        createDoctor("Dr Busy " + unique("y").replace("-", ""), dept, "Oncologist", "900.00");

        mockMvc.perform(patch("/api/admin/departments/{id}/status", id).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HAS_ACTIVE_DOCTORS"));

        mockMvc.perform(get("/api/admin/departments").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].activeDoctorCount".formatted(id)).value(1));
    }

    @Test
    void nonAdminsCannotManageDepartmentsOrDoctors() throws Exception {
        MockHttpSession patient = loginAs(Role.PATIENT);
        MockHttpSession doctor = loginAs(Role.DOCTOR);
        mockMvc.perform(post("/api/admin/departments").with(csrf()).session(patient)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hack\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/doctors").session(doctor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/patients").session(doctor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/doctors")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- admin: doctors

    @Test
    void adminCreatesDoctorWhoReceivesSetupLinkAndAppearsInDirectory() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        Department dept = createDepartment(unique("Cardio"));
        String email = uniqueEmail("newdoc");
        String license = unique("REG").toUpperCase();

        String body = mockMvc.perform(post("/api/admin/doctors").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Dr Priya Test", email, dept.getId(), license, "650.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.departmentName").value(dept.getName()))
                .andExpect(jsonPath("$.consultationFee").value(650.50))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long doctorId = Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(setupMailExists(email)).isTrue();

        mockMvc.perform(get("/api/doctors").param("departmentId", dept.getId().toString()))
                .andExpect(jsonPath("$.content[0].id").value(doctorId));

        // duplicate e-mail and duplicate licence
        mockMvc.perform(post("/api/admin/doctors").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Dr Other", email, dept.getId(), unique("REG"), "100")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        mockMvc.perform(post("/api/admin/doctors").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Dr Other", uniqueEmail("x"), dept.getId(), license.toLowerCase(), "100")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LICENSE_ALREADY_REGISTERED"));
    }

    @Test
    void doctorCreationValidatesInput() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        mockMvc.perform(post("/api/admin/doctors").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"X","email":"bad","phone":"12","departmentId":null,"specialization":"",
                                 "qualifications":"","licenseNumber":"has space","experienceYears":-1,
                                 "consultationFee":-5,"slotDurationMinutes":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("departmentId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("consultationFee")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("slotDurationMinutes")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("licenseNumber")));

        mockMvc.perform(post("/api/admin/doctors").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Dr Nowhere", uniqueEmail("nd"), 999999L, unique("REG"), "100")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminUpdatesDoctorDepartmentAndFee() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        Department a = createDepartment(unique("DeptA"));
        Department b = createDepartment(unique("DeptB"));
        Doctor doc = createDoctor("Dr Move Me", a, "Physician", "300.00");

        mockMvc.perform(put("/api/admin/doctors/{id}", doc.getId()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Dr Moved", null, b.getId(), doc.getLicenseNumber(), "450.00").replace("\"email\":\"null\",", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(b.getId()))
                .andExpect(jsonPath("$.consultationFee").value(450.00))
                .andExpect(jsonPath("$.fullName").value("Dr Moved"));
    }

    // ------------------------------------------------------------- admin: staff

    @Test
    void adminCreatesReceptionistWithEmployeeCodeButNotDoctorsOrPatientsViaStaffEndpoint() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String email = uniqueEmail("desk");
        mockMvc.perform(post("/api/admin/staff").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECEPTIONIST\",\"fullName\":\"Rita Desk\",\"email\":\"%s\",\"phone\":\"+91 97777 66666\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeCode", matchesPattern("EMP-\\d{4}")))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));
        assertThat(setupMailExists(email)).isTrue();

        mockMvc.perform(post("/api/admin/staff").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PATIENT\",\"fullName\":\"No Way\",\"email\":\"%s\",\"phone\":\"+91 97777 66666\"}".formatted(uniqueEmail("p"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_ROLE"));

        mockMvc.perform(get("/api/admin/staff").param("role", "RECEPTIONIST").param("search", email).session(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void newStaffMemberCanSetPasswordFromSetupLinkAndLogIn() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String email = uniqueEmail("labnew");
        mockMvc.perform(post("/api/admin/staff").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"LAB_TECHNICIAN\",\"fullName\":\"Lab New\",\"email\":\"%s\",\"phone\":\"+91 97777 55555\",\"qualification\":\"DMLT\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qualification").value("DMLT"));

        String token = readToken(email);
        mockMvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(token, PASSWORD, PASSWORD)))
                .andExpect(status().isOk());
        MockHttpSession lab = login(email);
        mockMvc.perform(get("/api/auth/me").session(lab))
                .andExpect(jsonPath("$.role").value("LAB_TECHNICIAN"))
                .andExpect(jsonPath("$.employeeCode").exists());
    }

    // ------------------------------------------------------------- admin: patients

    @Test
    void adminSearchesCorrectsAndFindsDuplicatePatients() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String name = "Dupli " + unique("Case").replace("-", "").replaceAll("\\d", "q");
        Patient p1 = createPatient(name, "+91 91111 22222");
        Patient p2 = createPatient(name.toUpperCase(), "+91 93333 44444");

        mockMvc.perform(get("/api/admin/patients").param("search", p1.getPatientCode()).session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].patientCode").value(p1.getPatientCode()));

        mockMvc.perform(get("/api/admin/patients/duplicates").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.matchReason == 'NAME_AND_DATE_OF_BIRTH')].patients[*].id",
                        hasItem(p2.getId().intValue())));

        mockMvc.perform(put("/api/admin/patients/{id}", p2.getId()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Corrected Name","phone":"+91 95555 66666","dateOfBirth":"1980-01-01",
                                 "gender":"OTHER","address":"Fixed","reason":"Typo at registration"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Corrected Name"))
                .andExpect(jsonPath("$.dateOfBirth").value("1980-01-01"));

        mockMvc.perform(put("/api/admin/patients/{id}", p2.getId()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"No Reason\",\"phone\":\"+91 95555 66666\",\"dateOfBirth\":\"1980-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("reason")));
    }

    // ------------------------------------------------------------- doctor self-service

    @Test
    void doctorEditsOwnProfileButNotFeeOrDepartment() throws Exception {
        Department dept = createDepartment(unique("Derm"));
        Doctor doc = createDoctor("Dr Self Edit", dept, "Dermatologist", "350.00");
        MockHttpSession session = login(doc.getUser().getEmail());

        mockMvc.perform(put("/api/doctor/profile").with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+91 90000 11111\",\"qualifications\":\"MBBS, MD (Derm)\",\"bio\":\"Updated bio\",\"consultationFee\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Updated bio"))
                .andExpect(jsonPath("$.consultationFee").value(350.00));

        MockHttpSession patient = loginAs(Role.PATIENT);
        mockMvc.perform(get("/api/doctor/profile").session(patient)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- helpers

    private Patient createPatient(String name, String phone) {
        User u = userRepository.save(new User(uniqueEmail("pt"), passwordHash(), name, phone, Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("T").toUpperCase().substring(0, 10), java.time.LocalDate.of(1975, 6, 15)));
    }

    private static boolean setupMailExists(String email) throws Exception {
        try (Stream<Path> files = Files.list(Path.of("target/test-mail-outbox"))) {
            return files.anyMatch(p -> {
                try {
                    String s = Files.readString(p);
                    return s.contains("To: " + email) && s.contains("staff account is ready");
                } catch (Exception e) {
                    return false;
                }
            });
        }
    }

    private static String readToken(String email) throws Exception {
        try (Stream<Path> files = Files.list(Path.of("target/test-mail-outbox"))) {
            String content = files.map(p -> {
                try {
                    return Files.readString(p);
                } catch (Exception e) {
                    return "";
                }
            }).filter(s -> s.contains("To: " + email)).findFirst().orElseThrow();
            return content.replaceAll("(?s).*token=([A-Za-z0-9_\\-]+).*", "$1");
        }
    }
}
