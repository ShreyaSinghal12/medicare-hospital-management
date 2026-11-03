package com.medicare.controller;

import java.util.Map;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.notification.NotificationResponse;
import com.medicare.dto.notification.UnreadCountResponse;
import com.medicare.notification.NotificationService;
import com.medicare.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Own in-app notifications. Users can only ever see and change their own. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List own notifications, newest first")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(CurrentUser.id(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications (navbar badge)")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(notificationService.unreadCount(CurrentUser.id()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(CurrentUser.id(), id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all own notifications as read")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead(CurrentUser.id()));
    }
}
