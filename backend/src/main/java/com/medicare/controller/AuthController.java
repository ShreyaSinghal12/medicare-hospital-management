package com.medicare.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import com.medicare.dto.auth.ForgotPasswordRequest;
import com.medicare.dto.auth.LoginRequest;
import com.medicare.dto.auth.MeResponse;
import com.medicare.dto.auth.RegisterPatientRequest;
import com.medicare.dto.auth.ResetPasswordRequest;
import com.medicare.dto.common.MessageResponse;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.enums.AuditOutcome;
import com.medicare.security.AppUserPrincipal;
import com.medicare.security.ClientIp;
import com.medicare.security.CurrentUser;
import com.medicare.security.SessionManager;
import com.medicare.service.AuditService;
import com.medicare.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, session login/logout and password recovery")
public class AuthController {

    static final String RESET_REQUESTED_MESSAGE =
            "If an account exists for that e-mail, a password reset link has been sent.";

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuditService auditService;

    public AuthController(AuthService authService, SessionManager sessionManager, AuditService auditService) {
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new patient account")
    public PatientProfileResponse register(@Valid @RequestBody RegisterPatientRequest request) {
        return authService.registerPatient(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in; starts a session (HTTP-only MEDICARE_SESSION cookie)")
    public MeResponse login(@Valid @RequestBody LoginRequest request,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUserPrincipal principal = authService.login(request, ClientIp.of(httpRequest));
        sessionManager.startSession(principal, httpRequest, httpResponse);
        return authService.me(principal.id());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out; invalidates the server-side session")
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        CurrentUser.find().ifPresent(p -> auditService.record(AuditService.Actions.LOGOUT, "User", p.id(),
                AuditOutcome.SUCCESS, null));
        sessionManager.endSession(httpRequest, httpResponse);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user (200), or 204 No Content when not logged in")
    public ResponseEntity<MeResponse> me() {
        return CurrentUser.find()
                .map(p -> ResponseEntity.ok(authService.me(p.id())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset e-mail (same response whether or not the e-mail exists)")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                          HttpServletRequest httpRequest) {
        authService.requestPasswordReset(request, ClientIp.of(httpRequest));
        return new MessageResponse(RESET_REQUESTED_MESSAGE);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a single-use reset token")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                         HttpServletRequest httpRequest) {
        authService.resetPassword(request, ClientIp.of(httpRequest));
        return new MessageResponse("Your password has been reset. You can now log in.");
    }
}
