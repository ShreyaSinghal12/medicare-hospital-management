package com.medicare.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import com.medicare.config.AppProperties;
import com.medicare.dto.account.ChangePasswordRequest;
import com.medicare.dto.auth.ForgotPasswordRequest;
import com.medicare.dto.auth.LoginRequest;
import com.medicare.dto.auth.MeResponse;
import com.medicare.dto.auth.RegisterPatientRequest;
import com.medicare.dto.auth.ResetPasswordRequest;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.entity.PasswordResetToken;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.NotificationType;
import com.medicare.enums.Role;
import com.medicare.exception.AuthenticationFailedException;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.PatientMapper;
import com.medicare.notification.EmailService;
import com.medicare.notification.NotificationService;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.PasswordResetTokenRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.ReceptionistRepository;
import com.medicare.repository.UserRepository;
import com.medicare.security.AppUserPrincipal;
import com.medicare.security.RateLimiter;
import com.medicare.validation.StrongPasswordValidator;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, password reset and password change.
 *
 * <p>Security measures implemented here:</p>
 * <ul>
 *   <li>Generic "invalid e-mail or password" errors and a dummy BCrypt check for unknown
 *       e-mails, so attackers can't discover which addresses are registered.</li>
 *   <li>Per-account lockout after {@code maxFailedLogins} wrong passwords.</li>
 *   <li>Per-IP rate limiting of login and password-reset requests.</li>
 *   <li>Reset tokens: 256-bit random, only the SHA-256 hash stored, single use, time-limited;
 *       a new request invalidates older links; the response is identical whether or not the
 *       e-mail exists.</li>
 *   <li>A password change/reset revokes every other session of that user
 *       (see {@code SessionValidationFilter}).</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ReceptionistRepository receptionistRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SequenceService sequenceService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PatientRepository patientRepository,
                       DoctorRepository doctorRepository, ReceptionistRepository receptionistRepository,
                       PasswordResetTokenRepository resetTokenRepository, PasswordEncoder passwordEncoder,
                       SequenceService sequenceService, AuditService auditService,
                       NotificationService notificationService, EmailService emailService,
                       RateLimiter rateLimiter, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.receptionistRepository = receptionistRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
        this.clock = Clock.systemUTC();
        // Used to spend the same BCrypt time when the e-mail does not exist.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // ------------------------------------------------------------------ registration

    @Transactional
    public PatientProfileResponse registerPatient(RegisterPatientRequest req) {
        String email = normalizeEmail(req.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this e-mail address already exists");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(req.password()),
                req.fullName().trim(), req.phone().trim(), Role.PATIENT));

        Patient patient = new Patient(user, sequenceService.nextPatientCode(), req.dateOfBirth());
        patient.setGender(req.gender());
        patient.setBloodGroup(req.bloodGroup());
        patient.setAddress(blankToNull(req.address()));
        patient.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        patient.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        patientRepository.save(patient);

        notificationService.notify(user.getId(), NotificationType.REGISTRATION, "Welcome to MediCare",
                "Your patient account has been created. Your patient ID is " + patient.getPatientCode() + ".",
                "/patient", "REGISTRATION:" + user.getId(), true);
        auditService.recordFor(user.getId(), user.getEmail(), Role.PATIENT.name(), AuditService.Actions.REGISTER,
                "Patient", patient.getId(), AuditOutcome.SUCCESS, "Self-registration");
        return PatientMapper.toProfile(patient);
    }

    // ------------------------------------------------------------------ login

    /**
     * Verifies credentials and returns the principal to store in the session.
     * Failed-attempt counters must be committed even though an exception is thrown,
     * hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AppUserPrincipal login(LoginRequest req, String clientIp) {
        AppProperties.Security cfg = appProperties.security();
        rateLimiter.check("login:" + clientIp, cfg.loginRequestsPerWindow(),
                Duration.ofMinutes(cfg.rateLimitWindowMinutes()));

        String email = normalizeEmail(req.email());
        Instant now = clock.instant();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        // No valid password can exceed BCrypt's 72-byte input limit (see StrongPasswordValidator).
        boolean tooLong = req.password().getBytes(StandardCharsets.UTF_8).length > StrongPasswordValidator.MAX_BCRYPT_BYTES;

        if (user == null || tooLong) {
            passwordEncoder.matches("timing-equaliser", dummyHash);
            auditService.recordFor(user == null ? null : user.getId(), email, user == null ? null : user.getRole().name(),
                    AuditService.Actions.LOGIN_FAILED, "User", user == null ? null : user.getId(),
                    AuditOutcome.FAILURE, user == null ? "Unknown account" : "Password longer than allowed");
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            auditService.recordFor(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditService.Actions.LOGIN_FAILED, "User", user.getId(), AuditOutcome.DENIED, "Account locked");
            throw accountLocked(user.getLockedUntil(), now);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= cfg.maxFailedLogins()) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(now.plus(Duration.ofMinutes(cfg.lockoutMinutes())));
                auditService.recordFor(user.getId(), user.getEmail(), user.getRole().name(),
                        AuditService.Actions.ACCOUNT_LOCKED, "User", user.getId(), AuditOutcome.FAILURE,
                        "Locked after " + cfg.maxFailedLogins() + " failed attempts");
                throw accountLocked(user.getLockedUntil(), now);
            }
            user.setFailedLoginAttempts(attempts);
            auditService.recordFor(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditService.Actions.LOGIN_FAILED, "User", user.getId(), AuditOutcome.FAILURE, "Wrong password");
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            auditService.recordFor(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditService.Actions.LOGIN_FAILED, "User", user.getId(), AuditOutcome.DENIED, "Account deactivated");
            throw new AuthenticationFailedException("ACCOUNT_DISABLED",
                    "This account has been deactivated. Please contact the hospital.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        auditService.recordFor(user.getId(), user.getEmail(), user.getRole().name(), AuditService.Actions.LOGIN,
                "User", user.getId(), AuditOutcome.SUCCESS, null);
        return AppUserPrincipal.from(user, now.truncatedTo(ChronoUnit.MICROS));
    }

    // ------------------------------------------------------------------ password reset

    /** Always completes silently, whether or not the address exists (prevents account enumeration). */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest req, String clientIp) {
        AppProperties.Security cfg = appProperties.security();
        rateLimiter.check("reset:" + clientIp, cfg.resetRequestsPerWindow(),
                Duration.ofMinutes(cfg.rateLimitWindowMinutes()));

        String email = normalizeEmail(req.email());
        User user = userRepository.findByEmailIgnoreCase(email).filter(User::isActive).orElse(null);
        if (user == null) {
            auditService.recordFor(null, email, null, AuditService.Actions.PASSWORD_RESET_REQUESTED, "User", null,
                    AuditOutcome.FAILURE, "Unknown or inactive account");
            return;
        }
        String link = createResetLink(user, Duration.ofMinutes(cfg.resetTokenMinutes()));
        emailService.send(user.getEmail(), "MediCare: reset your password",
                "Hello " + user.getFullName() + ",\n\n"
                        + "We received a request to reset your MediCare password. Open the link below to choose a new one.\n"
                        + "The link is valid for " + cfg.resetTokenMinutes() + " minutes and can be used once.\n\n"
                        + link + "\n\n"
                        + "If you did not request this, you can ignore this e-mail; your password will not change.");
        auditService.recordFor(user.getId(), user.getEmail(), user.getRole().name(),
                AuditService.Actions.PASSWORD_RESET_REQUESTED, "User", user.getId(), AuditOutcome.SUCCESS, null);
    }

    /**
     * Sends a newly created staff account its "set your password" e-mail. Staff accounts are
     * created with a random unknown password, so administrators never see or choose passwords.
     * The link uses the same single-use token mechanism as password reset, with a longer validity.
     */
    @Transactional
    public void sendAccountSetupLink(User user) {
        int hours = appProperties.security().accountSetupHours();
        String link = createResetLink(user, Duration.ofHours(hours));
        emailService.send(user.getEmail(), "MediCare: your staff account is ready",
                "Hello " + user.getFullName() + ",\n\n"
                        + "An administrator has created a MediCare account for you (" + user.getRole().name().replace('_', ' ').toLowerCase(Locale.ROOT) + ").\n"
                        + "Open the link below to choose your password. It is valid for " + hours
                        + " hours and can be used once.\n\n" + link + "\n\n"
                        + "If the link has expired, use \"Forgot password\" on the login page.");
    }

    /** Creates a single-use reset token (only its hash is stored) and returns the frontend link. */
    private String createResetLink(User user, Duration validity) {
        Instant now = clock.instant();
        resetTokenRepository.invalidateAllForUser(user.getId(), now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        resetTokenRepository.save(new PasswordResetToken(user, sha256(rawToken), now.plus(validity)));
        return appProperties.frontendBaseUrl() + "/reset-password?token=" + rawToken;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req, String clientIp) {
        AppProperties.Security cfg = appProperties.security();
        rateLimiter.check("reset:" + clientIp, cfg.resetRequestsPerWindow(),
                Duration.ofMinutes(cfg.rateLimitWindowMinutes()));

        Instant now = clock.instant();
        PasswordResetToken token = resetTokenRepository.findByTokenHash(sha256(req.token().trim()))
                .filter(t -> t.isUsable(now) && t.getUser().isActive())
                .orElseThrow(() -> new BusinessRuleException("RESET_TOKEN_INVALID",
                        "This password reset link is invalid or has expired. Please request a new one."));

        User user = token.getUser();
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        token.markUsed(now);
        resetTokenRepository.invalidateAllForUser(user.getId(), now);

        notificationService.notify(user.getId(), NotificationType.ACCOUNT, "Password reset",
                "Your password was reset. If this wasn't you, contact the hospital immediately.",
                null, null, true);
        auditService.recordFor(user.getId(), user.getEmail(), user.getRole().name(),
                AuditService.Actions.PASSWORD_RESET, "User", user.getId(), AuditOutcome.SUCCESS, null);
    }

    // ------------------------------------------------------------------ change password

    /** Changes the logged-in user's password and returns a refreshed principal for the current session. */
    @Transactional
    public AppUserPrincipal changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        boolean currentTooLong = req.currentPassword().getBytes(StandardCharsets.UTF_8).length
                > StrongPasswordValidator.MAX_BCRYPT_BYTES;
        if (currentTooLong || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            auditService.recordIndependently(AuditService.Actions.PASSWORD_CHANGED, "User", userId, AuditOutcome.FAILURE,
                    "Wrong current password");
            throw new BusinessRuleException("CURRENT_PASSWORD_INCORRECT", "Your current password is incorrect");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("PASSWORD_UNCHANGED", "The new password must be different from the current one");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        notificationService.notify(user.getId(), NotificationType.ACCOUNT, "Password changed",
                "Your password was changed. Other devices have been signed out.", null, null, true);
        auditService.record(AuditService.Actions.PASSWORD_CHANGED, "User", userId, AuditOutcome.SUCCESS, null);
        return AppUserPrincipal.from(user, user.getPasswordChangedAt());
    }

    // ------------------------------------------------------------------ current user

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Long patientId = null;
        String patientCode = null;
        Long doctorId = null;
        String departmentName = null;
        String employeeCode = null;
        switch (u.getRole()) {
            case PATIENT -> {
                var p = patientRepository.findByUserId(u.getId()).orElse(null);
                if (p != null) {
                    patientId = p.getId();
                    patientCode = p.getPatientCode();
                }
            }
            case DOCTOR -> {
                var d = doctorRepository.findByUserId(u.getId()).orElse(null);
                if (d != null) {
                    doctorId = d.getId();
                    departmentName = d.getDepartment().getName();
                }
            }
            case RECEPTIONIST -> employeeCode = receptionistRepository.findByUserId(u.getId())
                    .map(r -> r.getEmployeeCode()).orElse(null);
            case ADMIN -> { /* no extra profile */ }
        }
        return new MeResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(),
                u.isEmailNotificationsEnabled(), u.getLastLoginAt(), patientId, patientCode, doctorId,
                departmentName, employeeCode);
    }

    // ------------------------------------------------------------------ helpers

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException("INVALID_CREDENTIALS", "Invalid e-mail or password");
    }

    private static AuthenticationFailedException accountLocked(Instant lockedUntil, Instant now) {
        long minutes = Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
        return new AuthenticationFailedException("ACCOUNT_LOCKED",
                "Too many failed attempts. The account is locked for about " + minutes + " minute(s).");
    }
}
