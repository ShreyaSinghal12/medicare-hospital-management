package com.medicare.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.schedule.DayAvailabilityDto;
import com.medicare.dto.schedule.DaySlotsResponse;
import com.medicare.dto.schedule.LeaveRequest;
import com.medicare.dto.schedule.LeaveResponse;
import com.medicare.dto.schedule.NextAvailableResponse;
import com.medicare.dto.schedule.SlotResponse;
import com.medicare.dto.schedule.WeeklyScheduleRequest;
import com.medicare.dto.schedule.WeeklyScheduleResponse;
import com.medicare.entity.Appointment;
import com.medicare.entity.Doctor;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.DoctorLeave;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.LeaveStatus;
import com.medicare.enums.LeaveType;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.notification.AppointmentNotifier;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.DoctorAvailabilityRepository;
import com.medicare.repository.DoctorLeaveRepository;
import com.medicare.repository.DoctorRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doctor working hours, leave/unavailability and slot generation.
 *
 * <p>Schedule changes never cancel existing appointments. Instead, future appointments that
 * no longer fit are flagged {@code needs_reschedule} and the patient is notified, so a
 * receptionist or the patient can move them.</p>
 */
@Service
public class ScheduleService {

    private static final int NEXT_AVAILABLE_SEARCH_DAYS = 60;

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorLeaveRepository leaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final HospitalSettingsService settingsService;
    private final HospitalClock clock;
    private final AppointmentNotifier notifier;
    private final AuditService auditService;

    public ScheduleService(DoctorRepository doctorRepository, DoctorAvailabilityRepository availabilityRepository,
                           DoctorLeaveRepository leaveRepository, AppointmentRepository appointmentRepository,
                           HospitalSettingsService settingsService, HospitalClock clock,
                           AppointmentNotifier notifier, AuditService auditService) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.leaveRepository = leaveRepository;
        this.appointmentRepository = appointmentRepository;
        this.settingsService = settingsService;
        this.clock = clock;
        this.notifier = notifier;
        this.auditService = auditService;
    }

    // ================================================================ weekly schedule

    @Transactional(readOnly = true)
    public WeeklyScheduleResponse getWeekly(Long doctorId) {
        Doctor doctor = findDoctor(doctorId);
        return toWeekly(doctor, availabilityRepository.findByDoctorIdOrderByDayOfWeek(doctorId), null);
    }

    /** Weekly hours shown on the public profile (only for bookable doctors). */
    @Transactional(readOnly = true)
    public WeeklyScheduleResponse getPublicWeekly(Long doctorId) {
        Doctor doctor = findBookableDoctor(doctorId);
        return toWeekly(doctor, availabilityRepository.findByDoctorIdOrderByDayOfWeek(doctorId), null);
    }

    @Transactional(readOnly = true)
    public Long doctorIdForUser(Long userId) {
        return doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for the current user")).getId();
    }

    @Transactional
    public WeeklyScheduleResponse updateWeekly(Long doctorId, WeeklyScheduleRequest req) {
        Doctor doctor = findDoctor(doctorId);
        validateWeek(req.days());
        Map<Integer, DoctorAvailability> existing = new HashMap<>();
        availabilityRepository.findByDoctorIdOrderByDayOfWeek(doctorId).forEach(a -> existing.put(a.getDayOfWeek(), a));

        for (DayAvailabilityDto d : req.days()) {
            DoctorAvailability row = existing.get(d.dayOfWeek());
            if (!d.working()) {
                if (row != null) {
                    row.update(row.getStartTime(), row.getEndTime(), row.getBreakStart(), row.getBreakEnd(), false);
                }
                continue;
            }
            if (row == null) {
                row = new DoctorAvailability(doctorId, d.dayOfWeek());
            }
            row.update(d.startTime(), d.endTime(), d.breakStart(), d.breakEnd(), true);
            availabilityRepository.save(row);
        }
        availabilityRepository.flush();
        int flagged = recheckFutureAppointments(doctor);
        auditService.record("DOCTOR_SCHEDULE_UPDATED", "Doctor", doctorId, AuditOutcome.SUCCESS,
                flagged > 0 ? flagged + " appointment(s) flagged for rescheduling" : null);
        return toWeekly(doctor, availabilityRepository.findByDoctorIdOrderByDayOfWeek(doctorId), flagged);
    }

    private void validateWeek(List<DayAvailabilityDto> days) {
        boolean[] seen = new boolean[8];
        for (DayAvailabilityDto d : days) {
            if (seen[d.dayOfWeek()]) {
                throw new BusinessRuleException("DUPLICATE_DAY", "Each day of the week must appear exactly once");
            }
            seen[d.dayOfWeek()] = true;
            if (!d.working()) {
                continue;
            }
            String day = java.time.DayOfWeek.of(d.dayOfWeek()).name();
            if (d.startTime() == null || d.endTime() == null || !d.endTime().isAfter(d.startTime())) {
                throw new BusinessRuleException("INVALID_HOURS", day + ": end time must be after start time");
            }
            boolean hasBreakStart = d.breakStart() != null;
            if (hasBreakStart != (d.breakEnd() != null)) {
                throw new BusinessRuleException("INVALID_BREAK", day + ": give both break start and break end, or neither");
            }
            if (hasBreakStart && (!d.breakEnd().isAfter(d.breakStart())
                    || d.breakStart().isBefore(d.startTime()) || d.breakEnd().isAfter(d.endTime()))) {
                throw new BusinessRuleException("INVALID_BREAK", day + ": the break must lie within working hours");
            }
        }
    }

    // ================================================================ slots

    @Transactional(readOnly = true)
    public DaySlotsResponse slotsFor(Long doctorId, LocalDate date) {
        Doctor doctor = findBookableDoctor(doctorId);
        return computeDay(doctor, date);
    }

    /** Same as {@link #slotsFor} but for internal use under an existing lock/transaction. */
    DaySlotsResponse computeDay(Doctor doctor, LocalDate date) {
        LocalDate today = clock.today();
        int duration = doctor.getSlotDurationMinutes();
        if (date.isBefore(today)) {
            return emptyDay(doctor, date, false, "This date is in the past.");
        }
        int windowDays = settingsService.getSettings().getBookingWindowDays();
        if (date.isAfter(today.plusDays(windowDays))) {
            return emptyDay(doctor, date, false, "Bookings open " + windowDays + " days in advance.");
        }
        DoctorAvailability day = availabilityRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrue(doctor.getId(), date.getDayOfWeek().getValue()).orElse(null);
        if (day == null) {
            return emptyDay(doctor, date, false, "The doctor does not work on this day.");
        }
        List<DoctorLeave> leave = leaveRepository.findApprovedOverlapping(doctor.getId(), date, date);
        if (leave.stream().anyMatch(l -> l.getLeaveType() == LeaveType.LEAVE)) {
            return emptyDay(doctor, date, true, "The doctor is on leave on this day.");
        }
        List<SlotCalculator.Interval> taken = appointmentRepository.findOccupying(doctor.getId(), date).stream()
                .map(a -> new SlotCalculator.Interval(a.getStartTime(), a.getEndTime())).toList();
        List<SlotResponse> slots = SlotCalculator.slots(day, duration, date, leave, taken, clock.now());
        long available = slots.stream().filter(SlotResponse::isAvailable).count();
        String message = null;
        if (slots.isEmpty()) {
            message = "No consultation slots on this day.";
        } else if (available == 0) {
            boolean anyBooked = slots.stream().anyMatch(s -> SlotCalculator.BOOKED.equals(s.status()));
            boolean allPastOrBooked = slots.stream().allMatch(s -> SlotCalculator.PAST.equals(s.status())
                    || SlotCalculator.BOOKED.equals(s.status()));
            message = !anyBooked && allPastOrBooked ? "No more slots are available today."
                    : anyBooked ? "All remaining slots on this day are taken."
                    : "The doctor is unavailable for the rest of this day.";
        }
        return new DaySlotsResponse(doctor.getId(), date, duration, true, message, available, slots);
    }

    /** Earliest available slot from {@code from} (default today) within the booking window. */
    @Transactional(readOnly = true)
    public NextAvailableResponse nextAvailable(Long doctorId, LocalDate from) {
        Doctor doctor = findBookableDoctor(doctorId);
        LocalDate today = clock.today();
        LocalDate start = from == null || from.isBefore(today) ? today : from;
        LocalDate last = today.plusDays(Math.min(settingsService.getSettings().getBookingWindowDays(), NEXT_AVAILABLE_SEARCH_DAYS));
        for (LocalDate d = start; !d.isAfter(last); d = d.plusDays(1)) {
            for (SlotResponse s : computeDay(doctor, d).slots()) {
                if (s.isAvailable()) {
                    return new NextAvailableResponse(doctorId, d, s.startTime(), s.endTime());
                }
            }
        }
        return null;
    }

    // ================================================================ leave

    @Transactional
    public LeaveResponse requestLeave(Long doctorId, LeaveRequest req, boolean autoApprove, Long actorUserId) {
        Doctor doctor = findDoctor(doctorId);
        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessRuleException("INVALID_DATES", "End date must be on or after the start date");
        }
        if (req.startDate().isBefore(clock.today())) {
            throw new BusinessRuleException("INVALID_DATES", "Leave cannot start in the past");
        }
        LocalTime start = null;
        LocalTime end = null;
        if (req.leaveType() == LeaveType.UNAVAILABLE) {
            if (req.startTime() == null || req.endTime() == null || !req.endTime().isAfter(req.startTime())) {
                throw new BusinessRuleException("INVALID_HOURS", "Give a start and end time (end after start) for unavailability");
            }
            start = req.startTime();
            end = req.endTime();
        }
        DoctorLeave leave = new DoctorLeave(doctor, req.leaveType(), req.startDate(), req.endDate(), start, end,
                req.reason() == null || req.reason().isBlank() ? null : req.reason().trim());
        leaveRepository.save(leave);
        Integer affected = null;
        if (autoApprove) {
            leave.review(LeaveStatus.APPROVED, actorUserId);
            leaveRepository.flush();
            affected = recheckFutureAppointments(doctor);
        }
        auditService.record(autoApprove ? "DOCTOR_LEAVE_RECORDED" : "DOCTOR_LEAVE_REQUESTED", "DoctorLeave",
                leave.getId(), AuditOutcome.SUCCESS, req.leaveType() + " " + req.startDate() + ".." + req.endDate());
        return toLeave(leave, affected);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> leaveForDoctor(Long doctorId) {
        return leaveRepository.findByDoctorIdOrderByStartDateDesc(doctorId).stream().map(l -> toLeave(l, null)).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveResponse> leaveForAdmin(LeaveStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        var result = status == null ? leaveRepository.findAllByOrderByCreatedAtDesc(pageable)
                : leaveRepository.findByStatusOrderByStartDateAsc(status, pageable);
        return PageResponse.from(result, l -> toLeave(l, null));
    }

    /** Admin approves or rejects a pending request. Approval flags affected appointments. */
    @Transactional
    public LeaveResponse decide(Long leaveId, boolean approve, Long adminUserId) {
        DoctorLeave leave = leaveRepository.findById(leaveId).orElseThrow(() -> new ResourceNotFoundException("Leave request", leaveId));
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessRuleException("LEAVE_ALREADY_DECIDED", "This request is already " + leave.getStatus());
        }
        leave.review(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED, adminUserId);
        leaveRepository.flush();
        Integer affected = approve ? recheckFutureAppointments(leave.getDoctor()) : null;
        notifier.leaveDecided(leave);
        auditService.record(approve ? "DOCTOR_LEAVE_APPROVED" : "DOCTOR_LEAVE_REJECTED", "DoctorLeave", leaveId,
                AuditOutcome.SUCCESS, affected != null && affected > 0 ? affected + " appointment(s) flagged" : null);
        return toLeave(leave, affected);
    }

    /** A doctor withdraws their own pending or approved future leave. */
    @Transactional
    public LeaveResponse cancelLeave(Long doctorId, Long leaveId) {
        DoctorLeave leave = leaveRepository.findById(leaveId)
                .filter(l -> l.getDoctor().getId().equals(doctorId))
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", leaveId));
        if (leave.getStatus() == LeaveStatus.REJECTED || leave.getStatus() == LeaveStatus.CANCELLED) {
            throw new BusinessRuleException("LEAVE_NOT_ACTIVE", "This request is already " + leave.getStatus());
        }
        if (leave.getEndDate().isBefore(clock.today())) {
            throw new BusinessRuleException("LEAVE_IN_PAST", "Past leave cannot be withdrawn");
        }
        leave.setStatus(LeaveStatus.CANCELLED);
        leaveRepository.flush();
        int affected = recheckFutureAppointments(leave.getDoctor());
        auditService.record("DOCTOR_LEAVE_WITHDRAWN", "DoctorLeave", leaveId, AuditOutcome.SUCCESS, null);
        return toLeave(leave, affected);
    }

    // ================================================================ re-check after changes

    /**
     * Re-evaluates every future SCHEDULED appointment of the doctor. Appointments that no
     * longer fit are flagged and the patient is notified; appointments that fit again are unflagged.
     *
     * @return number of appointments newly flagged
     */
    int recheckFutureAppointments(Doctor doctor) {
        LocalDateTime now = clock.now();
        List<Appointment> future = appointmentRepository.findScheduledFrom(doctor.getId(), now.toLocalDate()).stream()
                .filter(a -> LocalDateTime.of(a.getAppointmentDate(), a.getStartTime()).isAfter(now))
                .toList();
        if (future.isEmpty()) {
            return 0;
        }
        LocalDate lastDate = future.getLast().getAppointmentDate();
        List<DoctorLeave> leave = leaveRepository.findApprovedOverlapping(doctor.getId(), now.toLocalDate(), lastDate);
        Map<Integer, DoctorAvailability> week = new HashMap<>();
        availabilityRepository.findByDoctorIdOrderByDayOfWeek(doctor.getId()).forEach(a -> week.put(a.getDayOfWeek(), a));

        List<Appointment> newlyFlagged = new ArrayList<>();
        for (Appointment a : future) {
            DoctorAvailability day = week.get(a.getAppointmentDate().getDayOfWeek().getValue());
            boolean fits = SlotCalculator.fitsSchedule(day, a.getAppointmentDate(), a.getStartTime(), a.getEndTime(), leave);
            if (!fits && !a.isNeedsReschedule()) {
                a.setNeedsReschedule(true);
                newlyFlagged.add(a);
            } else if (fits && a.isNeedsReschedule()) {
                a.setNeedsReschedule(false);
            }
        }
        newlyFlagged.forEach(notifier::needsReschedule);
        return newlyFlagged.size();
    }

    // ================================================================ helpers

    Doctor findDoctor(Long id) {
        return doctorRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
    }

    /** Doctors patients can book: account active and department active. */
    Doctor findBookableDoctor(Long id) {
        Doctor d = findDoctor(id);
        if (!d.getUser().isActive() || !d.getDepartment().isActive()) {
            throw new ResourceNotFoundException("Doctor", id);
        }
        return d;
    }

    private DaySlotsResponse emptyDay(Doctor doctor, LocalDate date, boolean working, String message) {
        return new DaySlotsResponse(doctor.getId(), date, doctor.getSlotDurationMinutes(), working, message, 0, List.of());
    }

    private WeeklyScheduleResponse toWeekly(Doctor doctor, List<DoctorAvailability> rows, Integer flagged) {
        Map<Integer, DoctorAvailability> byDay = new HashMap<>();
        rows.forEach(r -> byDay.put(r.getDayOfWeek(), r));
        List<DayAvailabilityDto> days = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            DoctorAvailability a = byDay.get(d);
            days.add(a == null
                    ? new DayAvailabilityDto(d, false, null, null, null, null)
                    : new DayAvailabilityDto(d, a.isActive(), a.getStartTime(), a.getEndTime(), a.getBreakStart(), a.getBreakEnd()));
        }
        return new WeeklyScheduleResponse(doctor.getId(), doctor.getUser().getFullName(), doctor.getSlotDurationMinutes(),
                days, flagged);
    }

    private static LeaveResponse toLeave(DoctorLeave l, Integer affected) {
        Doctor d = l.getDoctor();
        return new LeaveResponse(l.getId(), d.getId(), d.getUser().getFullName(), d.getDepartment().getName(),
                l.getLeaveType(), l.getStartDate(), l.getEndDate(), l.getStartTime(), l.getEndTime(), l.getReason(),
                l.getStatus(), l.getReviewedAt(), l.getCreatedAt(), affected);
    }
}
