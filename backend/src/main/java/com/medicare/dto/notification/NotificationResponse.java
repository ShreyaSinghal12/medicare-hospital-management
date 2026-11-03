package com.medicare.dto.notification;

import java.time.Instant;

import com.medicare.enums.NotificationType;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String link,
        boolean read,
        Instant readAt,
        Instant createdAt) {
}
