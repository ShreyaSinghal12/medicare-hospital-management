package com.medicare.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.medicare.dto.appointment.AppointmentHistoryResponse;
import com.medicare.dto.appointment.AppointmentResponse;
import com.medicare.dto.appointment.BookAppointmentRequest;
import com.medicare.dto.appointment.CancelAppointmentRequest;
import com.medicare.dto.appointment.DoctorDashboardSummary;
import com.medicare.dto.appointment.PatientAppointmentSummary;
import com.medicare.dto.appointment.RescheduleAppointmentRequest;
import com.medicare.dto.common.PageResponse;
import com.medicare.dto.schedule.SlotResponse;
import com.medicare.entity.Appointment;
import com.medicare.entity.AppointmentSlot;
import com.medicare.entity.AppointmentStatusHistory;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.Role;
import com.medicare.enums.SlotStatus;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.AppointmentMapper;
import com.medicare.notification.AppointmentNotifier;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.AppointmentSlotRepository;
import com.medicare.repository.AppointmentStatusHistoryRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.QueueEntryRepository;
import com.medicare.entity.QueueEntry;
import com.medicare.security.AppUserPrincipal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appointment booking, cancellation, rescheduling and status changes.
 *
 * <h2>Double-booking prevention</h2>
 * <ol>
 *   <li>The doctor row is locked ({@code SELECT … FOR UPDATE}) at the start of the transaction,
 *       so concurrent bookings for the same doctor are processed one after another.</li>
 *   <li>The requested time must exactly match a currently AVAILABLE generated slot, re-computed
 *       under the lock (working hours, break, leave, existing appointments, past, booking window).</li>
 *   <li>The slot is reserved in {@code appointment_slots}, whose unique key (doctor, date, start)
 *       rejects a duplicate even if the lock were bypassed.</li>
 *   <li>{@code appointments} has a second unique key on (doctor, date, start) that ignores
 *       cancelled rows. Any unique-key violation is reported as HTTP 409 SLOT_ALREADY_BOOKED.</li>
 * </ol>
 * Every method takes the acting user explicitly, which keeps authorisation visible and testable.
 */
@Service
public class AppointmentService {

    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<Role> FRONT_DESK = EnumSet.of(Role.RECEPTIONIST, Role.ADMIN);
    private static final int MAX_PATIENT_RESCHEDULES = 3;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final ScheduleService scheduleService;
    private final SequenceService sequenceService;
    private final HospitalSettingsService settingsService;
    private final HospitalClock clock;
    private final AppointmentNotifier notifier;
    private final AuditService auditService;
    private final QueueService queueService;
    private final QueueEntryRepository queueRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, AppointmentSlotRepository slotRepository,
                              AppointmentStatusHistoryRepository historyRepository, DoctorRepository doctorRepository,
                              PatientRepository patientRepository, ScheduleService scheduleService,
                              SequenceService sequenceService, HospitalSettingsService settingsService,
                              HospitalClock clock, AppointmentNotifier notifier, AuditService auditService,
                              QueueService queueService, QueueEntryRepository queueRepository) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.historyRepository = historyRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.scheduleService = scheduleService;
        this.sequenceService = sequenceService;
        this.settingsService = settingsService;
        this.clock = clock;
        this.notifier = notifier;
        this.auditService = auditService;
        this.queueService = queueService;
        this.queueRepository = queueRepository;
    }

    // ================================================================ booking

    /**
     * READ COMMITTED: after the doctor-row lock is acquired, the availability and overlap checks must see
     * bookings committed by transactions that held the lock before us (REPEATABLE READ would reuse an older
     * snapshot). The unique keys remain the final guard either way.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse book(AppUserPrincipal actor, BookAppointmentRequest req) {
        Patient patient = resolvePatientForBooking(actor, req.patientId());
        // (1) serialise all reservations for this doctor
        Doctor doctor = doctorRepository.lockById(req.doctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", req.doctorId()));
        if (!doctor.getUser().isActive() || !doctor.getDepartment().isActive()) {
            throw new BusinessRuleException("DOCTOR_UNAVAILABLE", "This doctor is not accepting appointments");
        }
        // (2) re-validate the slot under the lock
        SlotResponse slot = requireAvailableSlot(doctor, req.date(), req.startTime(), null);
        if (appointmentRepository.patientHasOverlap(patient.getId(), req.date(), slot.startTime(), slot.endTime(), null)) {
            throw new ConflictException("PATIENT_TIME_CONFLICT", "The patient already has another appointment at this time");
        }
        AppointmentType type = Boolean.TRUE.equals(req.walkIn()) && FRONT_DESK.contains(actor.role())
                ? AppointmentType.WALK_IN : AppointmentType.NEW;
        if (req.followUpOfId() != null) {
            Appointment original = appointmentRepository.findById(req.followUpOfId())
                    .filter(o -> o.getPatient().getId().equals(patient.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment", req.followUpOfId()));
            if (original.getStatus() != AppointmentStatus.COMPLETED) {
                throw new BusinessRuleException("FOLLOW_UP_INVALID", "Follow-ups can only be booked for completed appointments");
            }
            type = AppointmentType.FOLLOW_UP;
        }

        try {
            // (3) reserve the slot in the ledger
            AppointmentSlot reserved = reserveSlot(doctor.getId(), req.date(), slot.startTime(), slot.endTime());
            // (4) create the appointment
            String reference = "APT-" + REF_DATE.format(req.date()) + "-" + String.format("%06d", sequenceService.next(SequenceService.APPOINTMENT));
            Appointment appointment = new Appointment(reference, patient, doctor, reserved, req.date(), slot.startTime(),
                    slot.endTime(), type, req.reason().trim(), actor.id());
            appointment.setFollowUpOfId(req.followUpOfId());
            appointmentRepository.saveAndFlush(appointment);
            history(appointment, null, AppointmentStatus.SCHEDULED, "BOOKED",
                    actor.role() == Role.PATIENT ? "Booked online by patient" : "Booked by " + actor.role().name().toLowerCase(), actor);
            notifier.booked(appointment);
            auditService.record("APPOINTMENT_BOOKED", "Appointment", appointment.getId(), AuditOutcome.SUCCESS, reference);
            return toResponse(appointment, actor);
        } catch (DataIntegrityViolationException ex) {
            throw slotTaken();
        }
    }

    // ================================================================ cancel / reschedule / no-show

    @Transactional
    public AppointmentResponse cancel(AppUserPrincipal actor, Long id, CancelAppointmentRequest req) {
        Appointment a = findForChange(id);
        if (!canCancel(actor, a)) {
            throw denyOrRule(actor, a, "cancel");
        }
        AppointmentStatus from = a.getStatus();
        a.cancel(actor.id(), req.reason().trim());
        releaseSlot(a);
        queueService.sync(a);
        history(a, from, AppointmentStatus.CANCELLED, "CANCELLED", "Reason: " + req.reason().trim(), actor);
        notifier.cancelled(a, actor.role() == Role.PATIENT);
        auditService.record("APPOINTMENT_CANCELLED", "Appointment", id, AuditOutcome.SUCCESS, a.getReferenceNo());
        return toResponse(a, actor);
    }

    /**
     * Moves an appointment to another slot of the same doctor. The new slot is reserved and the
     * old one released in the same transaction: if anything fails, nothing changes.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse reschedule(AppUserPrincipal actor, Long id, RescheduleAppointmentRequest req) {
        Appointment a = findForChange(id);
        if (!canReschedule(actor, a)) {
            throw denyOrRule(actor, a, "reschedule");
        }
        Doctor doctor = doctorRepository.lockById(a.getDoctor().getId()).orElseThrow();
        if (req.date().equals(a.getAppointmentDate()) && req.startTime().equals(a.getStartTime())) {
            throw new BusinessRuleException("SAME_SLOT", "Choose a different date or time");
        }
        SlotResponse slot = requireAvailableSlot(doctor, req.date(), req.startTime(), a.getId());
        if (appointmentRepository.patientHasOverlap(a.getPatient().getId(), req.date(), slot.startTime(), slot.endTime(), a.getId())) {
            throw new ConflictException("PATIENT_TIME_CONFLICT", "The patient already has another appointment at this time");
        }
        String previousWhen = AppointmentNotifier.when(a);
        try {
            AppointmentSlot newSlot = reserveSlot(doctor.getId(), req.date(), slot.startTime(), slot.endTime());
            releaseSlot(a);
            a.moveTo(newSlot, req.date(), slot.startTime(), slot.endTime());
            appointmentRepository.saveAndFlush(a);
        } catch (DataIntegrityViolationException ex) {
            throw slotTaken();
        }
        String details = "From " + previousWhen + " to " + AppointmentNotifier.when(a)
                + (req.reason() == null || req.reason().isBlank() ? "" : ". Reason: " + req.reason().trim());
        history(a, AppointmentStatus.SCHEDULED, AppointmentStatus.SCHEDULED, "RESCHEDULED", details, actor);
        notifier.rescheduled(a, previousWhen);
        auditService.record("APPOINTMENT_RESCHEDULED", "Appointment", id, AuditOutcome.SUCCESS, a.getReferenceNo());
        return toResponse(a, actor);
    }

    /** Doctor (own appointment) or front desk marks a patient who did not attend, after the start time. */
    @Transactional
    public AppointmentResponse markNoShow(AppUserPrincipal actor, Long id) {
        Appointment a = findForChange(id);
        boolean allowed = FRONT_DESK.contains(actor.role()) || isOwnDoctor(actor, a);
        if (!allowed) {
            throw new AccessDeniedException("Not allowed");
        }
        if (!LocalDateTime.of(a.getAppointmentDate(), a.getStartTime()).isBefore(clock.now())) {
            throw new BusinessRuleException("TOO_EARLY", "A no-show can only be recorded after the appointment start time");
        }
        AppointmentStatus from = a.transitionTo(AppointmentStatus.NO_SHOW);
        history(a, from, AppointmentStatus.NO_SHOW, "NO_SHOW", null, actor);
        queueService.sync(a);
        auditService.record("APPOINTMENT_NO_SHOW", "Appointment", id, AuditOutcome.SUCCESS, a.getReferenceNo());
        return toResponse(a, actor);
    }

    /** Reception check-in (PATCH /api/appointments/{id}/check-in): issues the queue token. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse checkIn(AppUserPrincipal actor, Long id) {
        if (!FRONT_DESK.contains(actor.role())) {
            throw new AccessDeniedException("Only reception can check patients in");
        }
        queueService.checkIn(actor, id);
        return toResponse(appointmentRepository.findWithDetailsById(id).orElseThrow(), actor);
    }

    /** Front desk: every appointment of a day (optionally one doctor / status), ordered by time. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> dayList(AppUserPrincipal actor, LocalDate date, Long doctorId, AppointmentStatus status) {
        if (!FRONT_DESK.contains(actor.role())) {
            throw new AccessDeniedException("Reception only");
        }
        LocalDate day = date == null ? clock.today() : date;
        Set<AppointmentStatus> statuses = status == null ? EnumSet.allOf(AppointmentStatus.class) : EnumSet.of(status);
        return responses(appointmentRepository.findForDay(day, doctorId, statuses), actor);
    }

    /** Front desk: all appointments of one patient, newest first. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> forPatient(AppUserPrincipal actor, Long patientId) {
        if (!FRONT_DESK.contains(actor.role())) {
            throw new AccessDeniedException("Reception only");
        }
        return responses(appointmentRepository.findByPatientIdOrderByAppointmentDateDescStartTimeDesc(patientId), actor);
    }

    // ================================================================ queries

    @Transactional(readOnly = true)
    public AppointmentResponse get(AppUserPrincipal actor, Long id) {
        Appointment a = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        requireView(actor, a);
        return toResponse(a, actor);
    }

    @Transactional(readOnly = true)
    public List<AppointmentHistoryResponse> history(AppUserPrincipal actor, Long id) {
        Appointment a = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        requireView(actor, a);
        return historyRepository.findByAppointmentIdOrderByChangedAtAscIdAsc(id).stream()
                .map(AppointmentMapper::toHistory).toList();
    }

    /**
     * @param scope UPCOMING (open, today or later), PAST (completed/no-show or earlier dates, not cancelled)
     *              or CANCELLED
     */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listForPatient(AppUserPrincipal actor, String scope, int page, int size) {
        Patient patient = patientRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
        LocalDate today = clock.today();
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50));
        var result = switch (scope == null ? "UPCOMING" : scope.toUpperCase()) {
            case "PAST" -> appointmentRepository.findPastForPatient(patient.getId(), today,
                    pageable.withSort(Sort.by(Sort.Direction.DESC, "appointmentDate", "startTime")));
            case "CANCELLED" -> appointmentRepository.findForPatient(patient.getId(), EnumSet.of(AppointmentStatus.CANCELLED),
                    null, null, pageable.withSort(Sort.by(Sort.Direction.DESC, "appointmentDate", "startTime")));
            default -> appointmentRepository.findForPatient(patient.getId(), AppointmentStatus.open(), today, null,
                    pageable.withSort(Sort.by("appointmentDate", "startTime")));
        };
        return pageResponses(result, actor);
    }

    @Transactional(readOnly = true)
    public PatientAppointmentSummary summaryForPatient(AppUserPrincipal actor) {
        Patient patient = patientRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
        Map<AppointmentStatus, Long> counts = new HashMap<>();
        for (Object[] row : appointmentRepository.countByStatusForPatient(patient.getId())) {
            counts.put((AppointmentStatus) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        var upcoming = appointmentRepository.findForPatient(patient.getId(), AppointmentStatus.open(), clock.today(), null,
                PageRequest.of(0, 1, Sort.by("appointmentDate", "startTime")));
        AppointmentResponse next = upcoming.isEmpty() ? null : toResponse(upcoming.getContent().getFirst(), actor);
        return new PatientAppointmentSummary(total, upcoming.getTotalElements(),
                counts.getOrDefault(AppointmentStatus.COMPLETED, 0L), counts.getOrDefault(AppointmentStatus.CANCELLED, 0L),
                counts.getOrDefault(AppointmentStatus.NO_SHOW, 0L), next);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listForDoctor(AppUserPrincipal actor, LocalDate from, LocalDate to,
                                                           AppointmentStatus status, int page, int size) {
        Doctor doctor = ownDoctor(actor);
        Set<AppointmentStatus> statuses = status == null ? EnumSet.allOf(AppointmentStatus.class) : EnumSet.of(status);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200), Sort.by("appointmentDate", "startTime"));
        return pageResponses(appointmentRepository.findForDoctor(doctor.getId(), statuses, from, to, pageable), actor);
    }

    @Transactional(readOnly = true)
    public DoctorDashboardSummary doctorDashboard(AppUserPrincipal actor) {
        Doctor doctor = ownDoctor(actor);
        LocalDate today = clock.today();
        var todays = appointmentRepository.findForDoctor(doctor.getId(), EnumSet.allOf(AppointmentStatus.class), today, today,
                PageRequest.of(0, 200, Sort.by("startTime"))).getContent();
        long pending = todays.stream().filter(a -> a.getStatus().isOpen()).count();
        long completed = todays.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        long week = 0;
        for (Object[] row : appointmentRepository.countByStatusForDoctor(doctor.getId(), weekStart, weekStart.plusDays(6))) {
            if (row[0] != AppointmentStatus.CANCELLED) {
                week += (Long) row[1];
            }
        }
        long upcoming = appointmentRepository.findForDoctor(doctor.getId(), EnumSet.of(AppointmentStatus.SCHEDULED),
                today.plusDays(1), null, PageRequest.of(0, 1)).getTotalElements();
        long todayActive = todays.stream().filter(a -> a.getStatus() != AppointmentStatus.CANCELLED).count();
        return new DoctorDashboardSummary(todayActive, pending, completed, week, upcoming,
                responses(todays, actor));
    }

    // ================================================================ authorisation rules

    private void requireView(AppUserPrincipal actor, Appointment a) {
        boolean allowed = switch (actor.role()) {
            case PATIENT -> a.getPatient().getUser().getId().equals(actor.id());
            case DOCTOR -> isOwnDoctor(actor, a);
            case RECEPTIONIST, ADMIN -> true;
            default -> false;
        };
        if (!allowed) {
            auditService.recordIndependently("APPOINTMENT_ACCESS_DENIED", "Appointment", a.getId(), AuditOutcome.DENIED, null);
            // 404 rather than 403 so ids of other patients' appointments are not confirmed
            throw new ResourceNotFoundException("Appointment", a.getId());
        }
    }

    boolean canCancel(AppUserPrincipal actor, Appointment a) {
        return switch (actor.role()) {
            case PATIENT -> isOwnPatient(actor, a) && a.getStatus() == AppointmentStatus.SCHEDULED && outsideCancellationWindow(a);
            case DOCTOR -> isOwnDoctor(actor, a) && a.getStatus() == AppointmentStatus.SCHEDULED && inFuture(a);
            case RECEPTIONIST, ADMIN -> a.getStatus() == AppointmentStatus.SCHEDULED || a.getStatus() == AppointmentStatus.CHECKED_IN;
            default -> false;
        };
    }

    boolean canReschedule(AppUserPrincipal actor, Appointment a) {
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            return false;
        }
        return switch (actor.role()) {
            case PATIENT -> isOwnPatient(actor, a) && (outsideCancellationWindow(a) || a.isNeedsReschedule())
                    && a.getRescheduleCount() < MAX_PATIENT_RESCHEDULES;
            case RECEPTIONIST, ADMIN -> true;
            default -> false;
        };
    }

    private RuntimeException denyOrRule(AppUserPrincipal actor, Appointment a, String action) {
        boolean owns = switch (actor.role()) {
            case PATIENT -> isOwnPatient(actor, a);
            case DOCTOR -> isOwnDoctor(actor, a);
            case RECEPTIONIST, ADMIN -> true;
            default -> false;
        };
        if (!owns) {
            return new ResourceNotFoundException("Appointment", a.getId());
        }
        if (!a.getStatus().isOpen() || (a.getStatus() != AppointmentStatus.SCHEDULED && !FRONT_DESK.contains(actor.role()))) {
            return new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "A " + a.getStatus().name().toLowerCase().replace('_', ' ') + " appointment cannot be " + (action.equals("cancel") ? "cancelled" : "rescheduled"));
        }
        if (actor.role() == Role.PATIENT && a.getRescheduleCount() >= MAX_PATIENT_RESCHEDULES && action.equals("reschedule")) {
            return new BusinessRuleException("RESCHEDULE_LIMIT", "This appointment has been rescheduled too many times. Please contact reception.");
        }
        if (actor.role() == Role.DOCTOR && action.equals("reschedule")) {
            return new AccessDeniedException("Doctors cannot reschedule appointments; please ask reception");
        }
        int hours = settingsService.getSettings().getCancellationWindowHours();
        return new BusinessRuleException("CANCELLATION_WINDOW_PASSED",
                "Appointments can only be changed online up to " + hours + " hour(s) before the start time. Please contact reception.");
    }

    private boolean outsideCancellationWindow(Appointment a) {
        int hours = settingsService.getSettings().getCancellationWindowHours();
        return LocalDateTime.of(a.getAppointmentDate(), a.getStartTime()).isAfter(clock.now().plusHours(hours));
    }

    private boolean inFuture(Appointment a) {
        return LocalDateTime.of(a.getAppointmentDate(), a.getStartTime()).isAfter(clock.now());
    }

    private static boolean isOwnPatient(AppUserPrincipal actor, Appointment a) {
        return a.getPatient().getUser().getId().equals(actor.id());
    }

    private static boolean isOwnDoctor(AppUserPrincipal actor, Appointment a) {
        return actor.role() == Role.DOCTOR && a.getDoctor().getUser().getId().equals(actor.id());
    }

    // ================================================================ helpers

    private Patient resolvePatientForBooking(AppUserPrincipal actor, Long patientId) {
        return switch (actor.role()) {
            case PATIENT -> {
                if (patientId != null) {
                    throw new AccessDeniedException("Patients can only book for themselves");
                }
                Patient p = patientRepository.findByUserId(actor.id())
                        .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
                yield p;
            }
            case RECEPTIONIST, ADMIN -> {
                if (patientId == null) {
                    throw new BusinessRuleException("PATIENT_REQUIRED", "Select the patient to book for");
                }
                Patient p = patientRepository.findWithUserById(patientId)
                        .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
                if (!p.getUser().isActive()) {
                    throw new BusinessRuleException("PATIENT_INACTIVE", "This patient account is deactivated");
                }
                yield p;
            }
            default -> throw new AccessDeniedException("Your role cannot book appointments");
        };
    }

    private SlotResponse requireAvailableSlot(Doctor doctor, LocalDate date, LocalTime start, Long ignoreAppointmentId) {
        var day = scheduleService.computeDay(doctor, date);
        SlotResponse slot = day.slots().stream().filter(s -> s.startTime().equals(start)).findFirst().orElse(null);
        if (slot == null) {
            throw new BusinessRuleException("SLOT_NOT_AVAILABLE",
                    day.message() != null ? day.message() : "The selected time is not a valid slot for this doctor");
        }
        if (slot.isAvailable()) {
            return slot;
        }
        if (SlotCalculator.BOOKED.equals(slot.status()) && ignoreAppointmentId != null) {
            // the only overlapping appointment may be the one being rescheduled
            boolean otherOverlap = appointmentRepository.doctorHasOverlap(doctor.getId(), date, slot.startTime(),
                    slot.endTime(), ignoreAppointmentId);
            if (!otherOverlap) {
                return slot;
            }
        }
        if (SlotCalculator.BOOKED.equals(slot.status())) {
            throw slotTaken();
        }
        throw new BusinessRuleException("SLOT_NOT_AVAILABLE", SlotCalculator.PAST.equals(slot.status())
                ? "This time has already passed" : "The doctor is unavailable at this time");
    }

    private AppointmentSlot reserveSlot(Long doctorId, LocalDate date, LocalTime start, LocalTime end) {
        AppointmentSlot slot = slotRepository.findForUpdate(doctorId, date, start).orElse(null);
        if (slot == null) {
            return slotRepository.saveAndFlush(new AppointmentSlot(doctorId, date, start, end));
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw slotTaken();
        }
        slot.book(end);
        return slotRepository.saveAndFlush(slot);
    }

    private void releaseSlot(Appointment a) {
        AppointmentSlot slot = a.getSlot();
        if (slot != null && slot.getStatus() == SlotStatus.BOOKED) {
            slot.release();
        }
    }

    private Appointment findForChange(Long id) {
        return appointmentRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    private Doctor ownDoctor(AppUserPrincipal actor) {
        return doctorRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for the current user"));
    }

    private void history(Appointment a, AppointmentStatus from, AppointmentStatus to, String action, String details,
                         AppUserPrincipal actor) {
        historyRepository.save(new AppointmentStatusHistory(a.getId(), from, to, action,
                details == null ? null : details.substring(0, Math.min(500, details.length())), actor.id()));
    }

    private AppointmentResponse toResponse(Appointment a, AppUserPrincipal actor) {
        return toResponse(a, actor, queueRepository.findByAppointmentId(a.getId()).orElse(null));
    }

    private AppointmentResponse toResponse(Appointment a, AppUserPrincipal actor, QueueEntry queue) {
        boolean showReason = !FRONT_DESK.contains(actor.role());
        return AppointmentMapper.toResponse(a, showReason, queue, canCancel(actor, a), canReschedule(actor, a));
    }

    /** Maps a list with one query for all queue entries instead of one per appointment. */
    private List<AppointmentResponse> responses(List<Appointment> list, AppUserPrincipal actor) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, QueueEntry> queue = new HashMap<>();
        queueRepository.findByAppointmentIdIn(list.stream().map(Appointment::getId).toList())
                .forEach(q -> queue.put(q.getAppointment().getId(), q));
        return list.stream().map(a -> toResponse(a, actor, queue.get(a.getId()))).toList();
    }

    private PageResponse<AppointmentResponse> pageResponses(org.springframework.data.domain.Page<Appointment> page,
                                                            AppUserPrincipal actor) {
        List<AppointmentResponse> content = responses(page.getContent(), actor);
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private static ConflictException slotTaken() {
        return new ConflictException("SLOT_ALREADY_BOOKED", "Sorry, this slot has just been booked. Please choose another time.");
    }
}
