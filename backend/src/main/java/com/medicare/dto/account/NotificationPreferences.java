package com.medicare.dto.account;

import java.util.Map;

import com.medicare.enums.EmailCategory;

import jakarta.validation.constraints.NotNull;

/**
 * E-mail preferences. {@code emailEnabled} is the master switch for optional e-mails; {@code categories} holds the
 * optional categories. Security e-mails are always sent and in-app notifications are always created.
 */
public record NotificationPreferences(@NotNull Boolean emailEnabled, @NotNull Map<EmailCategory, Boolean> categories) {
}
