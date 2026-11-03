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

    public AccountController(AccountService accountService, AuthService authService, SessionManager sessionManager) {
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
}
