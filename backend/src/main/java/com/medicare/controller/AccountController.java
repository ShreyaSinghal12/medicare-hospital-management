package com.medicare.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import com.medicare.dto.account.ChangePasswordRequest;
import com.medicare.dto.account.UpdateAccountRequest;
import com.medicare.dto.auth.MeResponse;
import com.medicare.dto.common.MessageResponse;
import com.medicare.security.AppUserPrincipal;
import com.medicare.security.CurrentUser;
import com.medicare.security.SessionManager;
import com.medicare.service.AccountService;
import com.medicare.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service account settings for every logged-in user. */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Own account settings and password change")
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;
    private final SessionManager sessionManager;

    private final com.medicare.notification.NotificationPreferenceService preferenceService;

    public AccountController(AccountService accountService, AuthService authService, SessionManager sessionManager,
                             com.medicare.notification.NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
        this.accountService = accountService;
        this.authService = authService;
        this.sessionManager = sessionManager;
    }

    @GetMapping
    @Operation(summary = "Get own account")
    public MeResponse get() {
        return authService.me(CurrentUser.id());
    }

    @PutMapping
    @Operation(summary = "Update own name, phone and e-mail notification preference")
    public MeResponse update(@Valid @RequestBody UpdateAccountRequest request) {
        return accountService.update(CurrentUser.id(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change own password (signs out other sessions, keeps this one)")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUserPrincipal refreshed = authService.changePassword(CurrentUser.id(), request);
        sessionManager.refreshPrincipal(refreshed, httpRequest, httpResponse);
        return new MessageResponse("Password changed successfully");
    }

    @GetMapping("/notification-preferences")
    @Operation(summary = "Own e-mail preferences (master switch and categories)")
    public com.medicare.dto.account.NotificationPreferences preferences() {
        return new com.medicare.dto.account.NotificationPreferences(authService.me(CurrentUser.id()).emailNotificationsEnabled(),
                preferenceService.get(CurrentUser.id()));
    }

    @PutMapping("/notification-preferences")
    @Operation(summary = "Update own e-mail preferences (security e-mails cannot be switched off)")
    public com.medicare.dto.account.NotificationPreferences updatePreferences(
            @Valid @RequestBody com.medicare.dto.account.NotificationPreferences request) {
        accountService.setEmailEnabled(CurrentUser.id(), request.emailEnabled());
        preferenceService.set(CurrentUser.id(), request.categories());
        return preferences();
    }
}
