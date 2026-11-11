package com.medicare.notification;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the reminder service every 15 minutes (configurable; disabled in tests). */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final ReminderService reminderService;
    private final boolean enabled;

    public ReminderJob(ReminderService reminderService, @Value("${medicare.reminders.enabled:true}") boolean enabled) {
        this.reminderService = reminderService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${medicare.reminders.interval:PT15M}", initialDelayString = "${medicare.reminders.initial-delay:PT1M}")
    public void run() {
        if (!enabled) {
            return;
        }
        ReminderService.RunResult r = reminderService.runDue(Instant.now());
        if (r.upcomingSent() + r.followUpsSent() > 0) {
            log.info("Reminders sent: {} upcoming, {} follow-up", r.upcomingSent(), r.followUpsSent());
        }
    }
}
