package com.medicare.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.medicare.dto.schedule.NextAvailableResponse;
import com.medicare.entity.Appointment;
import com.medicare.entity.AppointmentReminder;
import com.medicare.entity.Consultation;
import com.medicare.entity.HospitalSettings;
import com.medicare.enums.NotificationType;
import com.medicare.enums.ReminderType;
import com.medicare.repository.AppointmentReminderRepository;
import com.medicare.repository.ReminderQueryRepository;
import com.medicare.service.HospitalSettingsService;
import com.medicare.service.ScheduleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends appointment and follow-up reminders.
 *
 * <ul>
 *   <li><b>Upcoming</b>: once per appointment time, when a SCHEDULED appointment starts within
 *       {@code reminder_hours_before} hours. Bookings made inside that window are skipped (the confirmation was
 *       just sent). A rescheduled appointment gets a new reminder for its new time.</li>
 *   <li><b>Follow-up</b>: once per finalized consultation, from two days before the follow-up date the doctor
 *       entered until that date, unless the patient already has a later visit with that doctor. The message
 *       includes the doctor's next free slot (a scheduling hint, never clinical advice).</li>
 *   <li>Nothing is sent between 21:00 and 08:00 hospital time, or to inactive accounts.</li>
 *   <li>Each reminder is claimed by inserting a row with a unique key in its own transaction, so it is sent at
 *       most once even if two servers run the job together.</li>
 * </ul>
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    static final LocalTime QUIET_END = LocalTime.of(8, 0);
    static final LocalTime QUIET_START = LocalTime.of(21, 0);
    private static final int FOLLOW_UP_LEAD_DAYS = 2;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ReminderQueryRepository queries;
    private final AppointmentReminderRepository reminderRepository;
    private final NotificationService notificationService;
    private final HospitalSettingsService settingsService;
    private final ScheduleService scheduleService;
    private final TransactionTemplate newTx;

    public ReminderService(ReminderQueryRepository queries, AppointmentReminderRepository reminderRepository,
                           NotificationService notificationService, HospitalSettingsService settingsService,
                           ScheduleService scheduleService, PlatformTransactionManager txManager) {
        this.queries = queries;
        this.reminderRepository = reminderRepository;
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.scheduleService = scheduleService;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record RunResult(int upcomingSent, int followUpsSent, boolean quietHours) {
    }

    /** Sends everything due at {@code now}. Safe to call repeatedly. */
    public RunResult runDue(Instant now) {
        HospitalSettings settings = settingsService.getSettings();
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        ZonedDateTime local = now.atZone(zone);
        if (local.toLocalTime().isBefore(QUIET_END) || !local.toLocalTime().isBefore(QUIET_START)) {
            return new RunResult(0, 0, true);
        }
        return new RunResult(sendUpcoming(now, zone, settings), sendFollowUps(local.toLocalDate()), false);
    }

    private int sendUpcoming(Instant now, ZoneId zone, HospitalSettings settings) {
        int hours = settings.getReminderHoursBefore();
        Instant windowEnd = now.plusSeconds(hours * 3600L);
        LocalDate from = now.atZone(zone).toLocalDate();
        LocalDate to = windowEnd.atZone(zone).toLocalDate();
        int sent = 0;
        for (Appointment a : queries.scheduledBetween(from, to)) {
            Instant start = a.getAppointmentDate().atTime(a.getStartTime()).atZone(zone).toInstant();
            Instant windowOpened = start.minusSeconds(hours * 3600L);
            boolean due = start.isAfter(now) && !start.isAfter(windowEnd);
            boolean bookedInsideWindow = a.getCreatedAt() != null && a.getCreatedAt().isAfter(windowOpened);
            if (!due || bookedInsideWindow || !a.getPatient().getUser().isActive()) {
                continue;
            }
            String key = "UPCOMING:" + a.getId() + ":" + a.getAppointmentDate() + "T" + a.getStartTime();
            String message = "Reminder: your appointment with " + a.getDoctor().getUser().getFullName() + " ("
                    + a.getDoctor().getDepartment().getName() + ") is on " + DATE.format(a.getAppointmentDate()) + " at "
                    + TIME.format(a.getStartTime()) + ". Reference " + a.getReferenceNo()
                    + ". Please arrive 10 minutes early. If you cannot come, cancel or reschedule in MediCare.";
            if (claimAndSend(new AppointmentReminder(ReminderType.UPCOMING, a.getId(), null, windowOpened, key),
                    a.getPatient().getUser().getId(), NotificationType.APPOINTMENT_REMINDER, "Appointment reminder", message,
                    "/patient/appointments/" + a.getId(), settings.isEmailRemindersEnabled())) {
                sent++;
            }
        }
        return sent;
    }

    private int sendFollowUps(LocalDate today) {
        HospitalSettings settings = settingsService.getSettings();
        int sent = 0;
        for (Consultation c : queries.followUpsBetween(today, today.plusDays(FOLLOW_UP_LEAD_DAYS))) {
            var visit = c.getAppointment();
            if (!c.getPatient().getUser().isActive()
                    || queries.hasLaterVisit(c.getPatient().getId(), c.getDoctor().getId(), visit.getId(), visit.getAppointmentDate())) {
                continue;
            }
            String key = "FOLLOW_UP:" + c.getId() + ":" + c.getFollowUpDate();
            if (reminderRepository.existsByIdempotencyKey(key)) {
                continue;
            }
            String doctor = c.getDoctor().getUser().getFullName();
            String message = doctor + " asked to see you again around " + DATE.format(c.getFollowUpDate()) + "."
                    + nextSlotHint(c.getDoctor().getId(), today) + " Book a follow-up visit in MediCare.";
            Instant scheduledFor = c.getFollowUpDate().minusDays(FOLLOW_UP_LEAD_DAYS).atStartOfDay(ZoneId.of(settings.getTimeZone())).toInstant();
            if (claimAndSend(new AppointmentReminder(ReminderType.FOLLOW_UP, null, c.getId(), scheduledFor, key),
                    c.getPatient().getUser().getId(), NotificationType.FOLLOW_UP_REMINDER, "Follow-up visit due", message,
                    "/patient/book/" + c.getDoctor().getId(), settings.isEmailRemindersEnabled())) {
                sent++;
            }
        }
        return sent;
    }

    /** " The next free time is Mon 16 Nov 2026 at 10:15." or an empty string. Never fails the reminder. */
    private String nextSlotHint(Long doctorId, LocalDate from) {
        try {
            NextAvailableResponse next = scheduleService.nextAvailable(doctorId, from);
            return next == null ? "" : " The next free time is " + DATE.format(next.date()) + " at " + TIME.format(next.startTime()) + ".";
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /** Claims the reminder (unique key) and sends it in one new transaction; false if already claimed elsewhere. */
    private boolean claimAndSend(AppointmentReminder reminder, Long userId, NotificationType type, String title, String message,
                                 String link, boolean email) {
        try {
            return Boolean.TRUE.equals(newTx.execute(status -> {
                if (reminderRepository.existsByIdempotencyKey(reminder.getIdempotencyKey())) {
                    return false;
                }
                AppointmentReminder saved = reminderRepository.saveAndFlush(reminder);
                notificationService.notify(userId, type, title, message, link, "REMINDER:" + reminder.getIdempotencyKey(), email);
                saved.markSent();
                return true;
            }));
        } catch (DataIntegrityViolationException ex) {
            return false; // another instance claimed it first
        } catch (RuntimeException ex) {
            log.warn("Reminder {} could not be sent: {}", reminder.getIdempotencyKey(), ex.getClass().getSimpleName());
            return false;
        }
    }
}
