package com.medicare.mapper;

import com.medicare.dto.notification.NotificationResponse;
import com.medicare.entity.Notification;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                n.isRead(), n.getReadAt(), n.getCreatedAt());
    }
}
