package com.medicare.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.dto.reception.PatientContactUpdateRequest;
import com.medicare.dto.reception.ReceptionDashboard;
import com.medicare.dto.reception.WalkInRegistrationRequest;
import com.medicare.entity.Appointment;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.NotificationType;
import com.medicare.enums.QueueStatus;
import com.medicare.enums.Role;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.PatientMapper;
import com.medicare.notification.NotificationService;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.QueueEntryRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Front desk: walk-in registration, patient directory (demographics only) and the daily overview. */
@Service
public class ReceptionService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueRepository;
    private final StaffAccountService staffAccountService;
    private final SequenceService sequenceService;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final HospitalClock clock;
    private final AuditService auditService;

    public ReceptionService(PatientRepository patientRepository, AppointmentRepository appointmentRepository,
                            QueueEntryRepository queueRepository, StaffAccountService staffAccountService,
                            SequenceService sequenceService, AuthService authService,
                            NotificationService notificationService, HospitalClock clock, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueRepository = queueRepository;
        this.staffAccountService = staffAccountService;
        this.sequenceService = sequenceService;
        this.authService = authService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.auditService = auditService;
    }

    /** Registers a walk-in patient; they get an e-mail link to set their own password. */
    @Transactional
    public PatientProfileResponse registerWalkIn(AppUserPrincipal actor, WalkInRegistrationRequest req) {
        User user = staffAccountService.createStaffUser(req.email(), req.fullName(), req.phone(), Role.PATIENT);
        Patient p = new Patient(user, sequenceService.nextPatientCode(), req.dateOfBirth());
        p.setGender(req.gender());
        p.setBloodGroup(req.bloodGroup());
        p.setAddress(blankToNull(req.address()));
        p.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        p.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        p.setRegisteredByUserId(actor.id());
        patientRepository.save(p);
        authService.sendPatientInvitation(user, p.getPatientCode());
        notificationService.notify(user.getId(), NotificationType.REGISTRATION, "Welcome to MediCare",
                "You were registered at reception. Your patient ID is " + p.getPatientCode() + ".", "/patient",
                "REGISTRATION:" + user.getId(), false);
        auditService.record("PATIENT_REGISTERED_AT_RECEPTION", "Patient", p.getId(), AuditOutcome.SUCCESS, p.getPatientCode());
        return PatientMapper.toProfile(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientProfileResponse> searchPatients(String search, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50), Sort.by("user.fullName"));
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(patientRepository.search(term, true, pageable), PatientMapper::toProfile);
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getPatient(Long id) {
        return PatientMapper.toProfile(find(id));
    }

    @Transactional
    public PatientProfileResponse updateContact(Long id, PatientContactUpdateRequest req) {
        Patient p = find(id);
        p.getUser().setPhone(req.phone().trim());
        p.setAddress(blankToNull(req.address()));
        p.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        p.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        patientRepository.flush();
        auditService.record("PATIENT_CONTACT_UPDATED_AT_RECEPTION", "Patient", id, AuditOutcome.SUCCESS, null);
        return PatientMapper.toProfile(p);
    }

    @Transactional(readOnly = true)
    public ReceptionDashboard dashboard() {
        LocalDate today = clock.today();
        Map<AppointmentStatus, Long> counts = new HashMap<>();
        for (Object[] row : appointmentRepository.countByStatusOnDate(today)) {
            counts.put((AppointmentStatus) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<Appointment> active = appointmentRepository.findForDay(today, null,
                EnumSet.complementOf(EnumSet.of(AppointmentStatus.CANCELLED)));
        Map<Long, long[]> perDoctor = new LinkedHashMap<>(); // [appointments, waiting, inConsultation, completed]
        Map<Long, String> names = new HashMap<>();
        for (Appointment a : active) {
            long[] c = perDoctor.computeIfAbsent(a.getDoctor().getId(), k -> new long[4]);
            names.put(a.getDoctor().getId(), a.getDoctor().getUser().getFullName());
            c[0]++;
        }
        queueRepository.findBoard(today, null).forEach(q -> {
            long[] c = perDoctor.computeIfAbsent(q.getDoctorId(), k -> new long[4]);
            names.putIfAbsent(q.getDoctorId(), q.getAppointment().getDoctor().getUser().getFullName());
            if (q.getStatus() == QueueStatus.WAITING) c[1]++;
            if (q.getStatus() == QueueStatus.IN_CONSULTATION) c[2]++;
            if (q.getStatus() == QueueStatus.COMPLETED) c[3]++;
        });
        var doctors = perDoctor.entrySet().stream()
                .map(e -> new ReceptionDashboard.DoctorQueueSummary(e.getKey(), names.get(e.getKey()), e.getValue()[0],
                        e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .sorted((x, y) -> x.doctorName().compareToIgnoreCase(y.doctorName()))
                .toList();
        ZoneId zone = clock.zone();
        long registered = patientRepository.countByCreatedAtBetween(today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant());
        return new ReceptionDashboard(today, total, counts.getOrDefault(AppointmentStatus.SCHEDULED, 0L),
                counts.getOrDefault(AppointmentStatus.CHECKED_IN, 0L), counts.getOrDefault(AppointmentStatus.IN_PROGRESS, 0L),
                counts.getOrDefault(AppointmentStatus.COMPLETED, 0L), counts.getOrDefault(AppointmentStatus.CANCELLED, 0L),
                counts.getOrDefault(AppointmentStatus.NO_SHOW, 0L),
                appointmentRepository.countByAppointmentDateAndAppointmentType(today, AppointmentType.WALK_IN),
                registered, doctors);
    }

    private Patient find(Long id) {
        return patientRepository.findWithUserById(id).orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
