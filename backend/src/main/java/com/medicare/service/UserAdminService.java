package com.medicare.service;

import com.medicare.dto.admin.UpdateUserStatusRequest;
import com.medicare.dto.admin.UserSummaryResponse;
import com.medicare.dto.common.PageResponse;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.NotificationType;
import com.medicare.enums.Role;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.UserMapper;
import com.medicare.notification.NotificationService;
import com.medicare.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator account management: search, activate/deactivate, unlock.
 * Works only with non-clinical account data (least privilege).
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public UserAdminService(UserRepository userRepository, AuditService auditService,
                            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> search(String search, Role role, Boolean active, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("fullName").ascending());
        return PageResponse.from(userRepository.search(term, role, active, pageable), UserMapper::toSummary);
    }

    /**
     * Activates or deactivates an account. A deactivated user's open sessions are
     * revoked on their next request. Admins can't deactivate themselves or the last
     * active administrator.
     */
    @Transactional
    public UserSummaryResponse setActive(Long actingAdminId, Long userId, UpdateUserStatusRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        boolean activate = req.active();
        if (!activate) {
            if (user.getId().equals(actingAdminId)) {
                throw new BusinessRuleException("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
            }
            if (user.getRole() == Role.ADMIN && user.isActive()
                    && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
                throw new BusinessRuleException("LAST_ADMIN", "The last active administrator cannot be deactivated");
            }
        }
        if (user.isActive() != activate) {
            user.setActive(activate);
            if (activate) {
                notificationService.notify(user.getId(), NotificationType.ACCOUNT, "Account activated",
                        "Your MediCare account has been activated.", null, null, false);
            }
            String reason = req.reason() == null || req.reason().isBlank() ? null : "Reason: " + req.reason().trim();
            auditService.record(activate ? AuditService.Actions.USER_ACTIVATED : AuditService.Actions.USER_DEACTIVATED,
                    "User", user.getId(), AuditOutcome.SUCCESS, reason);
        }
        return UserMapper.toSummary(user);
    }

    @Transactional
    public UserSummaryResponse unlock(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        auditService.record(AuditService.Actions.USER_UNLOCKED, "User", user.getId(), AuditOutcome.SUCCESS, null);
        return UserMapper.toSummary(user);
    }
}
