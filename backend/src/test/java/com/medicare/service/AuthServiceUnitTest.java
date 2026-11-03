package com.medicare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.medicare.config.AppProperties;
import com.medicare.dto.auth.ForgotPasswordRequest;
import com.medicare.dto.auth.LoginRequest;
import com.medicare.entity.User;
import com.medicare.enums.Role;
import com.medicare.exception.AuthenticationFailedException;
import com.medicare.notification.EmailService;
import com.medicare.notification.NotificationService;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.LabTechnicianRepository;
import com.medicare.repository.PasswordResetTokenRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.PharmacistRepository;
import com.medicare.repository.ReceptionistRepository;
import com.medicare.repository.UserRepository;
import com.medicare.security.RateLimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Pure unit tests of {@link AuthService} with Mockito mocks (no Spring context, no database). */
class AuthServiceUnitTest {

    UserRepository userRepository = mock(UserRepository.class);
    PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    EmailService emailService = mock(EmailService.class);
    AuditService auditService = mock(AuditService.class);
    AuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        AppProperties props = new AppProperties("http://localhost:5173", List.of("http://localhost:5173"), "./uploads",
                new AppProperties.Mail(false, "no-reply@test", ""),
                new AppProperties.Security(3, 15, 100, 100, 15, 30));
        service = new AuthService(userRepository, mock(PatientRepository.class), mock(DoctorRepository.class),
                mock(ReceptionistRepository.class), mock(LabTechnicianRepository.class), mock(PharmacistRepository.class),
                tokenRepository, encoder, mock(SequenceService.class), auditService, mock(NotificationService.class),
                emailService, new RateLimiter(), props);
    }

    @Test
    void thirdWrongPasswordLocksAccountWhenLimitIsThree() {
        User user = new User("a@test.example", "$2a$real", "A", "123", Role.PATIENT);
        when(userRepository.findByEmailIgnoreCase("a@test.example")).thenReturn(Optional.of(user));
        when(encoder.matches(eq("bad"), any())).thenReturn(false);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("a@test.example", "bad"), "1.1.1.1"))
                    .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
        }
        assertThatThrownBy(() -> service.login(new LoginRequest("A@Test.example ", "bad"), "1.1.1.1"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_LOCKED");
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void successfulLoginResetsCounterAndRecordsLastLogin() {
        User user = new User("ok@test.example", "$2a$real", "Ok", "123", Role.DOCTOR);
        user.setFailedLoginAttempts(2);
        when(userRepository.findByEmailIgnoreCase("ok@test.example")).thenReturn(Optional.of(user));
        when(encoder.matches("Right@123", "$2a$real")).thenReturn(true);

        var principal = service.login(new LoginRequest("ok@test.example", "Right@123"), "1.1.1.1");

        assertThat(principal.role()).isEqualTo(Role.DOCTOR);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void forgotPasswordForUnknownEmailSendsNothing() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        service.requestPasswordReset(new ForgotPasswordRequest("ghost@test.example"), "1.1.1.1");
        verify(emailService, never()).send(anyString(), anyString(), anyString());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordStoresOnlyTheHashOfTheToken() {
        User user = new User("r@test.example", "$2a$real", "R", "123", Role.PATIENT);
        user.setActive(true);
        when(userRepository.findByEmailIgnoreCase("r@test.example")).thenReturn(Optional.of(user));
        var saved = new com.medicare.entity.PasswordResetToken[1];
        when(tokenRepository.save(any())).thenAnswer(inv -> saved[0] = inv.getArgument(0));
        var body = new String[1];
        when(emailService.send(eq("r@test.example"), anyString(), anyString())).thenAnswer(inv -> {
            body[0] = inv.getArgument(2);
            return EmailService.Result.SKIPPED;
        });

        service.requestPasswordReset(new ForgotPasswordRequest("r@test.example"), "1.1.1.1");

        String rawToken = body[0].replaceAll("(?s).*token=([A-Za-z0-9_\\-]+).*", "$1");
        assertThat(saved[0].getTokenHash()).hasSize(64).isEqualTo(AuthService.sha256(rawToken)).isNotEqualTo(rawToken);
    }
}
