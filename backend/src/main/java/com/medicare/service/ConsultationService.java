package com.medicare.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.medicare.dto.clinical.AmendConsultationRequest;
import com.medicare.dto.clinical.AmendmentResponse;
import com.medicare.dto.clinical.ConsultationContent;
import com.medicare.dto.clinical.ConsultationResponse;
import com.medicare.entity.Appointment;
import com.medicare.entity.AppointmentStatusHistory;
import com.medicare.entity.Consultation;
import com.medicare.entity.ConsultationAmendment;
import com.medicare.entity.Doctor;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.ConsultationStatus;
import com.medicare.enums.Role;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.ClinicalMapper;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.AppointmentStatusHistoryRepository;
import com.medicare.repository.ConsultationAmendmentRepository;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Doctor consultations: start → draft → finalize (completes the appointment) → audited amendments.
 *
 * <p>Only the doctor of the appointment can start, edit, finalize or amend its consultation.
 * Other doctors with a treatment relationship may read it. Patients may read their own
 * consultations once finalized (without clinician-only fields). Receptionists and
 * administrators have no access to clinical content.</p>
 */
@Service
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final ConsultationAmendmentRepository amendmentRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final DoctorRepository doctorRepository;
    private final ClinicalAccessService accessService;
    private final HospitalClock clock;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;

    public ConsultationService(ConsultationRepository consultationRepository,
                               ConsultationAmendmentRepository amendmentRepository,
                               AppointmentRepository appointmentRepository,
                               AppointmentStatusHistoryRepository historyRepository, DoctorRepository doctorRepository,
                               ClinicalAccessService accessService, HospitalClock clock, AuditService auditService,
                               JsonMapper jsonMapper) {
        this.consultationRepository = consultationRepository;
        this.amendmentRepository = amendmentRepository;
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.doctorRepository = doctorRepository;
        this.accessService = accessService;
        this.clock = clock;
        this.auditService = auditService;
        this.jsonMapper = jsonMapper;
    }

    // ================================================================ start

    /**
     * Starts (or re-opens) the consultation of the doctor's own appointment. A SCHEDULED
     * appointment for today is implicitly checked in first (until reception check-in exists),
     * so the status rules SCHEDULED → CHECKED_IN → IN_PROGRESS are always respected.
     */
    @Transactional
    public ConsultationResponse start(AppUserPrincipal actor, Long appointmentId) {
        Doctor doctor = accessService.requireDoctor(actor);
        Appointment appt = appointmentRepository.findWithDetailsById(appointmentId)
                .filter(a -> a.getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        var existing = consultationRepository.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            return view(existing.get(), doctor);
        }
        if (appt.getStatus() == AppointmentStatus.SCHEDULED || appt.getStatus() == AppointmentStatus.CHECKED_IN) {
            if (!appt.getAppointmentDate().equals(clock.today())) {
                throw new BusinessRuleException("NOT_TODAY", "Consultations can only be started on the day of the appointment");
            }
            if (appt.getStatus() == AppointmentStatus.SCHEDULED) {
                appt.transitionTo(AppointmentStatus.CHECKED_IN);
                history(appt, AppointmentStatus.SCHEDULED, AppointmentStatus.CHECKED_IN, "CHECKED_IN",
                        "Checked in by the doctor when starting the consultation", actor);
            }
            appt.transitionTo(AppointmentStatus.IN_PROGRESS);
            history(appt, AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS, "CONSULTATION_STARTED", null, actor);
        } else if (appt.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "A consultation cannot be started for a " + appt.getStatus().name().toLowerCase().replace('_', ' ') + " appointment");
        }
        Consultation consultation = consultationRepository.saveAndFlush(new Consultation(appt));
        auditService.record("CONSULTATION_STARTED", "Consultation", consultation.getId(), AuditOutcome.SUCCESS,
                appt.getReferenceNo());
        return view(consultation, doctor);
    }

    // ================================================================ read

    @Transactional
    public ConsultationResponse get(AppUserPrincipal actor, Long id) {
        Consultation c = find(id);
        if (actor.role() == Role.PATIENT) {
            if (!c.getPatient().getUser().getId().equals(actor.id()) || c.getStatus() != ConsultationStatus.FINALIZED) {
                throw new ResourceNotFoundException("Consultation", id);
            }
            return ClinicalMapper.toResponse(c, false, false, false);
        }
        Doctor doctor = accessService.requireTreatingDoctor(actor, c.getPatient().getId(), "CONSULTATION_VIEWED");
        if (c.getStatus() == ConsultationStatus.DRAFT && !isAuthor(c, doctor)) {
            throw new ResourceNotFoundException("Consultation", id); // drafts are private to the author
        }
        return view(c, doctor);
    }

    @Transactional
    public ConsultationResponse getByAppointment(AppUserPrincipal actor, Long appointmentId) {
        Consultation c = consultationRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation for appointment", appointmentId));
        return get(actor, c.getId());
    }

    @Transactional
    public List<AmendmentResponse> amendments(AppUserPrincipal actor, Long id) {
        Consultation c = find(id);
        accessService.requireTreatingDoctor(actor, c.getPatient().getId(), "CONSULTATION_AMENDMENTS_VIEWED");
        return amendmentRepository.findByConsultationIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(a -> new AmendmentResponse(a.getId(), a.getReason(),
                        doctorRepository.findWithDetailsById(a.getAmendedByDoctorId()).map(d -> d.getUser().getFullName()).orElse("—"),
                        a.getCreatedAt(), readSnapshot(a.getPreviousSnapshot())))
                .toList();
    }

    // ================================================================ write

    @Transactional
    public ConsultationResponse saveDraft(AppUserPrincipal actor, Long id, ConsultationContent content) {
        Consultation c = find(id);
        Doctor doctor = requireAuthor(actor, c);
        if (c.getStatus() != ConsultationStatus.DRAFT) {
            throw new BusinessRuleException("CONSULTATION_FINALIZED", "This consultation is finalized. Use an amendment to change it.");
        }
        checkVersion(c, content);
        apply(c, content);
        consultationRepository.saveAndFlush(c);
        return view(c, doctor);
    }

    /** Saves the given content, then finalizes the note and completes the appointment. */
    @Transactional
    public ConsultationResponse finalizeConsultation(AppUserPrincipal actor, Long id, ConsultationContent content) {
        Consultation c = find(id);
        Doctor doctor = requireAuthor(actor, c);
        if (c.getStatus() != ConsultationStatus.DRAFT) {
            throw new BusinessRuleException("CONSULTATION_FINALIZED", "This consultation is already finalized");
        }
        checkVersion(c, content);
        apply(c, content);
        if (isBlank(c.getDiagnosis())) {
            throw new BusinessRuleException("DIAGNOSIS_REQUIRED", "Enter a diagnosis (or working diagnosis) before finalizing");
        }
        c.finalizeNote();
        Appointment appt = c.getAppointment();
        if (appt.getStatus() == AppointmentStatus.IN_PROGRESS) {
            appt.transitionTo(AppointmentStatus.COMPLETED);
            history(appt, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED, "CONSULTATION_COMPLETED", null, actor);
        }
        consultationRepository.saveAndFlush(c);
        auditService.record("CONSULTATION_FINALIZED", "Consultation", id, AuditOutcome.SUCCESS, appt.getReferenceNo());
        return view(c, doctor);
    }

    /**
     * Amends a finalized consultation. The previous values are stored as a JSON snapshot together
     * with the reason, so the full history of the clinical record is preserved.
     */
    @Transactional
    public ConsultationResponse amend(AppUserPrincipal actor, Long id, AmendConsultationRequest req) {
        Consultation c = find(id);
        Doctor doctor = requireAuthor(actor, c);
        if (c.getStatus() != ConsultationStatus.FINALIZED) {
            throw new BusinessRuleException("CONSULTATION_NOT_FINALIZED", "Drafts can be edited directly");
        }
        checkVersion(c, req.content());
        Map<String, Object> previous = snapshot(c);
        apply(c, req.content());
        if (isBlank(c.getDiagnosis())) {
            throw new BusinessRuleException("DIAGNOSIS_REQUIRED", "A finalized consultation must keep a diagnosis");
        }
        if (previous.equals(snapshot(c))) {
            throw new BusinessRuleException("NO_CHANGES", "The amendment does not change anything");
        }
        amendmentRepository.save(new ConsultationAmendment(c.getId(), doctor.getId(), req.reason().trim(),
                jsonMapper.writeValueAsString(previous)));
        c.recordAmendment();
        consultationRepository.saveAndFlush(c);
        auditService.record("CONSULTATION_AMENDED", "Consultation", id, AuditOutcome.SUCCESS,
                "Amendment #" + c.getAmendmentCount());
        return view(c, doctor);
    }

    // ================================================================ helpers

    private void apply(Consultation c, ConsultationContent in) {
        if (in.followUpDate() != null && !in.followUpDate().isAfter(clock.today())) {
            throw new BusinessRuleException("INVALID_FOLLOW_UP", "The follow-up date must be in the future");
        }
        c.applyContent(trim(in.symptoms()), trim(in.observations()), trim(in.diagnosis()), trim(in.treatmentPlan()),
                trim(in.notes()), in.followUpDate(), trim(in.followUpNotes()));
    }

    private static void checkVersion(Consultation c, ConsultationContent in) {
        if (in.version() != null && in.version() != c.getVersion()) {
            throw new ConflictException("CONCURRENT_MODIFICATION",
                    "This consultation was changed in another window. Reload it before saving.");
        }
    }

    private static Map<String, Object> snapshot(Consultation c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symptoms", c.getSymptoms());
        m.put("observations", c.getObservations());
        m.put("diagnosis", c.getDiagnosis());
        m.put("treatmentPlan", c.getTreatmentPlan());
        m.put("notes", c.getNotes());
        m.put("followUpDate", c.getFollowUpDate() == null ? null : c.getFollowUpDate().toString());
        m.put("followUpNotes", c.getFollowUpNotes());
        return m;
    }

    private Map<String, Object> readSnapshot(String json) {
        return jsonMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private Doctor requireAuthor(AppUserPrincipal actor, Consultation c) {
        Doctor doctor = accessService.requireDoctor(actor);
        if (!isAuthor(c, doctor)) {
            throw new AccessDeniedException("Only the consulting doctor can change this consultation");
        }
        return doctor;
    }

    private static boolean isAuthor(Consultation c, Doctor doctor) {
        return c.getDoctor().getId().equals(doctor.getId());
    }

    private ConsultationResponse view(Consultation c, Doctor viewer) {
        boolean author = isAuthor(c, viewer);
        return ClinicalMapper.toResponse(c, true, author && c.getStatus() == ConsultationStatus.DRAFT,
                author && c.getStatus() == ConsultationStatus.FINALIZED);
    }

    private Consultation find(Long id) {
        return consultationRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
    }

    private void history(Appointment a, AppointmentStatus from, AppointmentStatus to, String action, String details,
                         AppUserPrincipal actor) {
        historyRepository.save(new AppointmentStatusHistory(a.getId(), from, to, action, details, actor.id()));
    }

    private static String trim(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
