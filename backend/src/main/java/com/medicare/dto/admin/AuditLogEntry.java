package com.medicare.dto.admin;

import java.time.Instant;

import com.medicare.enums.AuditOutcome;

public record AuditLogEntry(Long id, Instant createdAt, Long actorUserId, String actorEmail, String actorRole, String action,
                            String entityType, String entityId, AuditOutcome outcome, String details, String ipAddress) {
}
