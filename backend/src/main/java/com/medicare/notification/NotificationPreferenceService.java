package com.medicare.notification;

import java.util.EnumMap;
import java.util.Map;

import com.medicare.enums.EmailCategory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-category e-mail preferences. A category without a stored row is enabled. */
@Service
public class NotificationPreferenceService {

    private final JdbcTemplate jdbc;

    public NotificationPreferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Optional categories with their current setting (all optional categories are present). */
    @Transactional(readOnly = true)
    public Map<EmailCategory, Boolean> get(Long userId) {
        Map<EmailCategory, Boolean> map = new EnumMap<>(EmailCategory.class);
        for (EmailCategory c : EmailCategory.values()) {
            if (c.isOptional()) {
                map.put(c, true);
            }
        }
        jdbc.query("SELECT category, enabled FROM notification_email_preferences WHERE user_id = ?",
                rs -> {
                    map.put(EmailCategory.valueOf(rs.getString(1)), rs.getBoolean(2));
                }, userId);
        return map;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId, EmailCategory category) {
        if (!category.isOptional()) {
            return category == EmailCategory.SECURITY;
        }
        Boolean enabled = jdbc.query("SELECT enabled FROM notification_email_preferences WHERE user_id = ? AND category = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null, userId, category.name());
        return enabled == null || enabled;
    }

    @Transactional
    public void set(Long userId, Map<EmailCategory, Boolean> settings) {
        settings.forEach((category, enabled) -> {
            if (category.isOptional() && enabled != null) {
                jdbc.update("""
                        INSERT INTO notification_email_preferences (user_id, category, enabled) VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)
                        """, userId, category.name(), enabled);
            }
        });
    }
}
