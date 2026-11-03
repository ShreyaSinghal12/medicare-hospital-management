package com.medicare.service;

import com.medicare.dto.account.UpdateAccountRequest;
import com.medicare.dto.auth.MeResponse;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service account settings shared by all roles (name, phone, e-mail preference). */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuditService auditService;

    public AccountService(UserRepository userRepository, AuthService authService, AuditService auditService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.auditService = auditService;
    }

    @Transactional
    public MeResponse update(Long userId, UpdateAccountRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone().trim());
        user.setEmailNotificationsEnabled(req.emailNotificationsEnabled());
        userRepository.flush();
        auditService.record(AuditService.Actions.ACCOUNT_UPDATED, "User", userId, AuditOutcome.SUCCESS,
                "Updated name/phone/e-mail preference");
        return authService.me(userId);
    }
}
