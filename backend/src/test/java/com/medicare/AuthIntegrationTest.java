package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.medicare.entity.PasswordResetToken;
import com.medicare.entity.User;
import com.medicare.enums.Role;
import com.medicare.repository.AuditLogRepository;
import com.medicare.repository.NotificationRepository;
import com.medicare.repository.PasswordResetTokenRepository;
import com.medicare.repository.UserRepository;
import com.medicare.service.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 2 authentication and authorization tests against the MySQL test database.
 * Each test uses unique e-mail addresses, so tests are independent of each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    static final String PASSWORD = "Secure@123";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------ helpers

    static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
    }

    static String registrationJson(String email, String password, String confirm) {
        return """
                {"fullName":"Test Patient","email":"%s","password":"%s","confirmPassword":"%s",
                 "phone":"+91 98765 43210","dateOfBirth":"1990-05-20","gender":"FEMALE","bloodGroup":"O+",
                 "address":"1 Test Lane","emergencyContactName":"Test Contact","emergencyContactPhone":"+91 91234 56789"}
                """.formatted(email, password, confirm);
    }

    MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    User createStaff(String email, Role role) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), "Staff " + role, "+91 90000 99999", role));
    }

    // ------------------------------------------------------------ registration

    @Test
    void registrationCreatesPatientWithReadableCodeAndWelcomeNotification() throws Exception {
        String email = uniqueEmail("reg");
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(email.toUpperCase(), PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientCode", matchesPattern("PAT-\\d{6}")))
                .andExpect(jsonPath("$.email").value(email))            // normalised to lower case
                .andExpect(jsonPath("$.bloodGroup").value("O+"))
                .andExpect(jsonPath("$.age").isNumber());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.PATIENT);
        assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(notificationRepository.countByUserIdAndReadFalse(user.getId())).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        String email = uniqueEmail("dup");
        register(email);
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(email, PASSWORD, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void weakPasswordAndMismatchedConfirmationReturnFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(uniqueEmail("weak"), "password", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registrationJson(uniqueEmail("mm"), PASSWORD, "Other@1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("confirmPassword")));
    }

    @Test
    void futureDateOfBirthIsRejected() throws Exception {
        String json = registrationJson(uniqueEmail("dob"), PASSWORD, PASSWORD).replace("1990-05-20", "2999-01-01");
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("dateOfBirth")));
    }

    // ------------------------------------------------------------ login / logout / me

    @Test
    void loginStartsSessionAndLogoutEndsIt() throws Exception {
        String email = uniqueEmail("login");
        register(email);

        mockMvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());

        MockHttpSession session = login(email, PASSWORD);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.patientCode").exists());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/patients/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGenericError() throws Exception {
        String email = uniqueEmail("wrong");
        register(email);
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Wrong@1234\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid e-mail or password"));
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Wrong@1234\"}".formatted(uniqueEmail("nobody"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid e-mail or password"));
    }

    @Test
    void accountLocksAfterFiveFailedAttemptsEvenWithCorrectPassword() throws Exception {
        String email = uniqueEmail("lock");
        register(email);
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"Wrong@1234\"}".formatted(email)))
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Wrong@1234\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.example\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    // ------------------------------------------------------------ authorization

    @Test
    void roleBasedAccessIsEnforcedOnTheServer() throws Exception {
        String patientEmail = uniqueEmail("rbac.p");
        register(patientEmail);
        MockHttpSession patient = login(patientEmail, PASSWORD);

        String doctorEmail = uniqueEmail("rbac.d");
        createStaff(doctorEmail, Role.DOCTOR);
        MockHttpSession doctor = login(doctorEmail, PASSWORD);

        String adminEmail = uniqueEmail("rbac.a");
        createStaff(adminEmail, Role.ADMIN);
        MockHttpSession admin = login(adminEmail, PASSWORD);

        mockMvc.perform(get("/api/admin/users").session(patient)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").session(doctor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/me").session(doctor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/me").session(admin)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/patients/me").session(patient)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").param("search", "rbac").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void deactivatingAUserRevokesTheirOpenSession() throws Exception {
        String patientEmail = uniqueEmail("deact");
        register(patientEmail);
        MockHttpSession patient = login(patientEmail, PASSWORD);
        Long patientUserId = userRepository.findByEmailIgnoreCase(patientEmail).orElseThrow().getId();

        String adminEmail = uniqueEmail("deact.admin");
        createStaff(adminEmail, Role.ADMIN);
        MockHttpSession admin = login(adminEmail, PASSWORD);

        mockMvc.perform(patch("/api/admin/users/{id}/status", patientUserId).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false,\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/patients/me").session(patient))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(patientEmail, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void adminCannotDeactivateThemselves() throws Exception {
        String adminEmail = uniqueEmail("self.admin");
        User admin = createStaff(adminEmail, Role.ADMIN);
        MockHttpSession session = login(adminEmail, PASSWORD);
        mockMvc.perform(patch("/api/admin/users/{id}/status", admin.getId()).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
    }

    // ------------------------------------------------------------ profile & account

    @Test
    void patientCanUpdateOwnProfileButValidationApplies() throws Exception {
        String email = uniqueEmail("profile");
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(put("/api/patients/me").with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Updated Name","phone":"+91 99999 11111","dateOfBirth":"1991-01-01",
                                 "gender":"FEMALE","bloodGroup":"AB-","address":"New address",
                                 "emergencyContactName":"EC","emergencyContactPhone":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.bloodGroup").value("AB-"))
                .andExpect(jsonPath("$.emergencyContactPhone").doesNotExist());

        mockMvc.perform(put("/api/patients/me").with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"<script>\",\"phone\":\"abc\",\"dateOfBirth\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("dateOfBirth")));
    }

    @Test
    void changePasswordKeepsCurrentSessionButRevokesOthers() throws Exception {
        String email = uniqueEmail("chpw");
        register(email);
        MockHttpSession current = login(email, PASSWORD);
        MockHttpSession other = login(email, PASSWORD);

        mockMvc.perform(put("/api/account/password").with(csrf()).session(current).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong@0000\",\"newPassword\":\"Newpass@456\",\"confirmPassword\":\"Newpass@456\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        mockMvc.perform(put("/api/account/password").with(csrf()).session(current).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Newpass@456\",\"confirmPassword\":\"Newpass@456\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/me").session(current)).andExpect(status().isOk());
        mockMvc.perform(get("/api/patients/me").session(other))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        login(email, "Newpass@456");
    }

    // ------------------------------------------------------------ password reset

    @Test
    void passwordResetFlowWorksOnceAndUnknownEmailLooksIdentical() throws Exception {
        String email = uniqueEmail("reset");
        register(email);

        String unknownBody = mockMvc.perform(post("/api/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"%s\"}".formatted(uniqueEmail("ghost"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String knownBody = mockMvc.perform(post("/api/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(knownBody).isEqualTo(unknownBody);

        String token = readResetTokenFromOutbox(email);

        String resetJson = "{\"token\":\"%s\",\"newPassword\":\"Reset@7890\",\"confirmPassword\":\"Reset@7890\"}".formatted(token);
        mockMvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(resetJson))
                .andExpect(status().isOk());
        // single use
        mockMvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(resetJson))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        login(email, "Reset@7890");
    }

    @Test
    void expiredResetTokenIsRejected() throws Exception {
        String email = uniqueEmail("expired");
        register(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String raw = "expired-token-" + UUID.randomUUID();
        tokenRepository.save(new PasswordResetToken(user, AuthService.sha256(raw), Instant.now().minus(1, ChronoUnit.MINUTES)));

        mockMvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"Reset@7890\",\"confirmPassword\":\"Reset@7890\"}".formatted(raw)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    // ------------------------------------------------------------ notifications

    @Test
    void usersSeeOnlyTheirOwnNotificationsAndCanMarkThemRead() throws Exception {
        String a = uniqueEmail("notif.a");
        String b = uniqueEmail("notif.b");
        register(a);
        register(b);
        MockHttpSession sa = login(a, PASSWORD);
        MockHttpSession sb = login(b, PASSWORD);

        mockMvc.perform(get("/api/notifications/unread-count").session(sa))
                .andExpect(jsonPath("$.unread").value(1));
        String body = mockMvc.perform(get("/api/notifications").session(sa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("REGISTRATION"))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1");

        // user B cannot mark user A's notification
        mockMvc.perform(patch("/api/notifications/{id}/read", id).with(csrf()).session(sb))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/notifications/{id}/read", id).with(csrf()).session(sa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(get("/api/notifications/unread-count").session(sa))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void loginAttemptsAreAudited() throws Exception {
        String email = uniqueEmail("audit");
        register(email);
        long before = auditLogRepository.count();
        login(email, PASSWORD);
        assertThat(auditLogRepository.count()).isGreaterThan(before);
    }

    // ------------------------------------------------------------ outbox helper

    private String readResetTokenFromOutbox(String email) throws IOException {
        Path dir = Path.of("target/test-mail-outbox");
        try (Stream<Path> files = Files.list(dir)) {
            Path file = files
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("To: " + email);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .max(Comparator.comparing(p -> p.toFile().lastModified()))
                    .orElseThrow(() -> new AssertionError("No reset e-mail written for " + email));
            Matcher m = Pattern.compile("reset-password\\?token=([A-Za-z0-9_\\-]+)").matcher(Files.readString(file));
            assertThat(m.find()).isTrue();
            return m.group(1);
        }
    }
}
