package com.medicare.service;

import com.medicare.entity.AuditLog;
import com.medicare.enums.AuditOutcome;
import com.medicare.repository.AuditLogRepository;
import com.medicare.security.AppUserPrincipal;
import com.medicare.security.ClientIp;
import com.medicare.security.CurrentUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the security/audit trail.
 *
 * <p>Each entry is written in its own transaction ({@code REQUIRES_NEW}) so that a
 * failed operation (e.g. a rejected login) is still recorded even when the
 * surrounding business transaction rolls back. Callers must never pass passwords,
 * tokens or clinical free text in {@code details}.</p>
 */
@Service
public class AuditService {

    /** Action names used across the application. */
    public static final class Actions {
        public static final String LOGIN = "LOGIN";
        public static final String LOGIN_FAILED = "LOGIN_FAILED";
        public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
        public static final String LOGOUT = "LOGOUT";
        public static final String REGISTER = "REGISTER_PATIENT";
        public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
        public static final String PASSWORD_RESET = "PASSWORD_RESET";
        public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
        public static final String ACCOUNT_UPDATED = "ACCOUNT_UPDATED";
        public static final String PATIENT_PROFILE_UPDATED = "PATIENT_PROFILE_UPDATED";
        public static final String USER_ACTIVATED = "USER_ACTIVATED";
        public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
        public static final String USER_UNLOCKED = "USER_UNLOCKED";

        private Actions() {
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAILS = 1000;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Records an action performed by the currently logged-in user (or anonymous). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Object entityId, AuditOutcome outcome, String details) {
        AppUserPrincipal actor = CurrentUser.find().orElse(null);
        write(actor == null ? null : actor.id(), actor == null ? null : actor.email(),
                actor == null ? null : actor.role().name(), action, entityType, entityId, outcome, details);
    }

    /** Records an action for an explicitly given actor (e.g. during login, before a session exists). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFor(Long actorUserId, String actorEmail, String actorRole, String action, String entityType,
                          Object entityId, AuditOutcome outcome, String details) {
        write(actorUserId, actorEmail, actorRole, action, entityType, entityId, outcome, details);
    }

    private void write(Long actorUserId, String actorEmail, String actorRole, String action, String entityType,
                       Object entityId, AuditOutcome outcome, String details) {
        try {
            repository.save(new AuditLog(actorUserId, truncate(actorEmail, 150), actorRole, action, entityType,
                    entityId == null ? null : String.valueOf(entityId), outcome, truncate(details, MAX_DETAILS),
                    ClientIp.current()));
        } catch (RuntimeException ex) {
            // Auditing must never break the user's request; the failure itself is logged without data.
            log.error("Failed to write audit log entry for action {}", action, ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
