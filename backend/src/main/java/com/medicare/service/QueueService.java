package com.medicare.service;

import java.time.LocalDate;
import java.util.List;

import com.medicare.dto.reception.QueueEntryResponse;
import com.medicare.entity.Appointment;
import com.medicare.entity.AppointmentStatusHistory;
import com.medicare.entity.QueueEntry;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.NotificationType;
import com.medicare.enums.QueueStatus;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.notification.NotificationService;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.AppointmentStatusHistoryRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.QueueEntryRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Check-in, queue tokens and queue/appointment status synchronisation.
 *
 * <p>Tokens are numbered 1, 2, 3, ... per doctor per day. They are allocated while holding the
 * doctor-row lock (the same lock bookings use) using a locking read of the current maximum, and the
 * unique key (doctor, date, token) is a database-level guard, so two simultaneous check-ins never
 * receive the same number.</p>
 */
@Service
public class QueueService {

    private final QueueEntryRepository queueRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final DoctorRepository doctorRepository;
    private final HospitalClock clock;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public QueueService(QueueEntryRepository queueRepository, AppointmentRepository appointmentRepository,
                        AppointmentStatusHistoryRepository historyRepository, DoctorRepository doctorRepository,
                        HospitalClock clock, NotificationService notificationService, AuditService auditService) {
        this.queueRepository = queueRepository;
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.doctorRepository = doctorRepository;
        this.clock = clock;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /** Reception check-in: SCHEDULED → CHECKED_IN on the appointment day, with a new queue token. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntry checkIn(AppUserPrincipal actor, Long appointmentId) {
        Appointment a = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        if (!a.getAppointmentDate().equals(clock.today())) {
            throw new BusinessRuleException("NOT_TODAY", "Patients can only be checked in on the day of their appointment");
        }
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Only scheduled appointments can be checked in (current status: " + a.getStatus() + ")");
        }
        a.transitionTo(AppointmentStatus.CHECKED_IN);
        historyRepository.save(new AppointmentStatusHistory(a.getId(), AppointmentStatus.SCHEDULED,
                AppointmentStatus.CHECKED_IN, "CHECKED_IN", "Checked in at reception", actor.id()));
        QueueEntry entry = issueToken(a);
        notificationService.notify(a.getDoctor().getUser().getId(), NotificationType.OPERATIONAL, "Patient checked in",
                a.getPatient().getUser().getFullName() + " (token " + entry.getTokenNumber() + ") is waiting.",
                "/doctor", "CHECKIN:" + a.getId(), false);
        auditService.record("APPOINTMENT_CHECKED_IN", "Appointment", a.getId(), AuditOutcome.SUCCESS,
                a.getReferenceNo() + " token " + entry.getTokenNumber());
        return entry;
    }

    /**
     * Keeps the queue entry in line with the appointment's status. Called inside the transaction of
     * every appointment status change. Creates a token when a doctor starts a consultation for a
     * patient who was never checked in at reception.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(Appointment a) {
        QueueEntry entry = queueRepository.findByAppointmentId(a.getId()).orElse(null);
        QueueStatus target = switch (a.getStatus()) {
            case CHECKED_IN -> QueueStatus.WAITING;
            case IN_PROGRESS -> QueueStatus.IN_CONSULTATION;
            case COMPLETED -> QueueStatus.COMPLETED;
            case CANCELLED, NO_SHOW -> QueueStatus.SKIPPED;
            case SCHEDULED -> null;
        };
        if (target == null) {
            return;
        }
        if (entry == null) {
            if (target == QueueStatus.SKIPPED || target == QueueStatus.COMPLETED && a.getCheckedInAt() == null) {
                return; // never entered the queue
            }
            entry = issueToken(a);
        }
        if (target == QueueStatus.WAITING && entry.getStatus() == QueueStatus.SKIPPED) {
            return; // stays skipped until reception puts the patient back
        }
        entry.moveTo(target);
    }

    /** Reception skips a waiting patient (e.g. did not answer the call). The appointment stays checked in. */
    @Transactional
    public QueueEntryResponse skip(Long entryId) {
        QueueEntry e = find(entryId);
        if (e.getStatus() != QueueStatus.WAITING) {
            throw new BusinessRuleException("QUEUE_NOT_WAITING", "Only waiting patients can be skipped");
        }
        e.moveTo(QueueStatus.SKIPPED);
        auditService.record("QUEUE_SKIPPED", "QueueEntry", entryId, AuditOutcome.SUCCESS, null);
        return toResponse(e);
    }

    /** Puts a skipped patient back in the queue (keeps their token number). */
    @Transactional
    public QueueEntryResponse requeue(Long entryId) {
        QueueEntry e = find(entryId);
        if (e.getStatus() != QueueStatus.SKIPPED || e.getAppointment().getStatus() != AppointmentStatus.CHECKED_IN) {
            throw new BusinessRuleException("QUEUE_NOT_SKIPPED", "Only skipped, still checked-in patients can be put back");
        }
        e.moveTo(QueueStatus.WAITING);
        auditService.record("QUEUE_REQUEUED", "QueueEntry", entryId, AuditOutcome.SUCCESS, null);
        return toResponse(e);
    }

    @Transactional(readOnly = true)
    public List<QueueEntryResponse> board(LocalDate date, Long doctorId) {
        return queueRepository.findBoard(date == null ? clock.today() : date, doctorId).stream().map(QueueService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<QueueEntryResponse> doctorQueue(Long userId) {
        Long doctorId = doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for the current user")).getId();
        return board(clock.today(), doctorId);
    }

    private QueueEntry issueToken(Appointment a) {
        doctorRepository.lockById(a.getDoctor().getId()); // serialise token allocation per doctor
        int next = queueRepository.maxToken(a.getDoctor().getId(), a.getAppointmentDate()) + 1;
        return queueRepository.saveAndFlush(new QueueEntry(a, next));
    }

    private QueueEntry find(Long id) {
        return queueRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Queue entry", id));
    }

    static QueueEntryResponse toResponse(QueueEntry q) {
        Appointment a = q.getAppointment();
        return new QueueEntryResponse(q.getId(), q.getTokenNumber(), q.getStatus(), a.getId(), a.getReferenceNo(),
                a.getStatus(), a.getStartTime(), a.getPatient().getId(), a.getPatient().getUser().getFullName(),
                a.getPatient().getPatientCode(), a.getDoctor().getId(), a.getDoctor().getUser().getFullName(),
                q.getCheckedInAt(), q.getCalledAt(), q.getCompletedAt());
    }
}
