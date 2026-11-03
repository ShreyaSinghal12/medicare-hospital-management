package com.medicare.notification;

import java.time.Instant;

import com.medicare.config.AppProperties;
import com.medicare.dto.common.PageResponse;
import com.medicare.dto.notification.NotificationResponse;
import com.medicare.entity.Notification;
import com.medicare.entity.User;
import com.medicare.enums.NotificationType;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.NotificationMapper;
import com.medicare.repository.NotificationRepository;
import com.medicare.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and reads in-app notifications, optionally mirroring them by e-mail.
 *
 * <p>Idempotency: when an {@code idempotencyKey} is given, a second call with the
 * same key does nothing (enforced by a unique index), so scheduler re-runs or
 * retried requests can't create duplicates.</p>
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public NotificationService(NotificationRepository repository, UserRepository userRepository,
                               EmailService emailService, AppProperties appProperties) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.frontendBaseUrl = appProperties.frontendBaseUrl();
    }

    /**
     * Stores a notification for a user. If {@code alsoEmail} is true and the user has
     * e-mail notifications enabled, a generic e-mail (title + message + link only) is sent.
     *
     * @return the stored notification, or {@code null} if the idempotency key was already used
     */
    @Transactional
    public Notification notify(Long userId, NotificationType type, String title, String message, String link,
                               String idempotencyKey, boolean alsoEmail) {
        if (idempotencyKey != null && repository.existsByIdempotencyKey(idempotencyKey)) {
            return null;
        }
        Notification notification = repository.save(
                new Notification(userId, type, title, message, link, idempotencyKey));

        if (alsoEmail) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.isEmailNotificationsEnabled()) {
                notification.setEmailStatus(Notification.EmailStatus.SKIPPED);
            } else {
                String body = message + (link != null ? "\n\nOpen MediCare: " + frontendBaseUrl + link : "")
                        + "\n\nThis is an automated message from MediCare. Please do not reply.";
                EmailService.Result result = emailService.send(user.getEmail(), "MediCare: " + title, body);
                notification.setEmailStatus(switch (result) {
                    case SENT, WRITTEN_TO_OUTBOX -> Notification.EmailStatus.SENT;
                    case SKIPPED -> Notification.EmailStatus.SKIPPED;
                    case FAILED -> Notification.EmailStatus.FAILED;
                });
            }
        }
        return notification;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        var result = unreadOnly
                ? repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(result, NotificationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    /** Marks one of the user's own notifications as read. Another user's id yields 404. */
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification n = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        n.markRead(Instant.now());
        return NotificationMapper.toResponse(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, Instant.now());
    }
}
