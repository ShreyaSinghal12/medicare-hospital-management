package com.medicare.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.prescription.PrescriptionItemDto;
import com.medicare.dto.prescription.PrescriptionRequest;
import com.medicare.dto.prescription.PrescriptionResponse;
import com.medicare.dto.prescription.PrescriptionSummary;
import com.medicare.entity.Consultation;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.entity.Prescription;
import com.medicare.entity.PrescriptionItem;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.NotificationType;
import com.medicare.enums.PrescriptionStatus;
import com.medicare.enums.Role;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.PrescriptionMapper;
import com.medicare.notification.NotificationService;
import com.medicare.reporting.PrescriptionPdfGenerator;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.PrescriptionRepository;
import com.medicare.security.AppUserPrincipal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Digital prescriptions: draft → finalize → (revise → finalize, superseding the previous version).
 *
 * <ul>
 *   <li>Only the doctor who wrote the consultation creates, edits, finalizes or revises.</li>
 *   <li>Finalized versions are immutable; a revision is a new row (same number, version + 1).</li>
 *   <li>Patients see only their own finalized and superseded versions; drafts are never visible to them.</li>
 *   <li>Other treating doctors may read non-draft versions; receptionists and administrators cannot.</li>
 *   <li>Medicine names, doses and instructions are typed by the doctor; nothing is suggested.</li>
 * </ul>
 */
@Service
public class PrescriptionService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<PrescriptionStatus> ISSUED = EnumSet.of(PrescriptionStatus.FINALIZED, PrescriptionStatus.SUPERSEDED);

    private final PrescriptionRepository prescriptionRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;
    private final ClinicalAccessService accessService;
    private final SequenceService sequenceService;
    private final HospitalClock clock;
    private final HospitalSettingsService settingsService;
    private final NotificationService notificationService;
    private final PrescriptionPdfGenerator pdfGenerator;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public PrescriptionService(PrescriptionRepository prescriptionRepository, ConsultationRepository consultationRepository,
                               PatientRepository patientRepository, ClinicalAccessService accessService,
                               SequenceService sequenceService, HospitalClock clock, HospitalSettingsService settingsService,
                               NotificationService notificationService, PrescriptionPdfGenerator pdfGenerator,
                               AuditService auditService, EntityManager entityManager) {
        this.prescriptionRepository = prescriptionRepository;
        this.consultationRepository = consultationRepository;
        this.patientRepository = patientRepository;
        this.accessService = accessService;
        this.sequenceService = sequenceService;
        this.clock = clock;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.pdfGenerator = pdfGenerator;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    // ================================================================ doctor: write

    @Transactional
    public PrescriptionResponse create(AppUserPrincipal actor, PrescriptionRequest req) {
        Doctor doctor = accessService.requireDoctor(actor);
        if (req.consultationId() == null) {
            throw new BusinessRuleException("CONSULTATION_REQUIRED", "A prescription must belong to a consultation");
        }
        Consultation consultation = consultationRepository.findWithDetailsById(req.consultationId())
                .filter(c -> c.getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", req.consultationId()));
        if (consultation.getAppointment().getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("APPOINTMENT_CANCELLED", "Cannot prescribe for a cancelled appointment");
        }
        boolean exists = prescriptionRepository.findByConsultationIdOrderByVersionNumberDesc(consultation.getId()).stream()
                .anyMatch(p -> p.getStatus() != PrescriptionStatus.SUPERSEDED);
        if (exists) {
            throw new ConflictException("PRESCRIPTION_EXISTS",
                    "This consultation already has a prescription. Edit the draft or revise the finalized one.");
        }
        String number = "RX-" + NO_DATE.format(clock.today()) + "-"
                + String.format("%06d", sequenceService.next(SequenceService.PRESCRIPTION));
        Prescription p = new Prescription(number, consultation);
        applyContent(p, req);
        prescriptionRepository.saveAndFlush(p);
        auditService.record("PRESCRIPTION_DRAFT_CREATED", "Prescription", p.getId(), AuditOutcome.SUCCESS, number);
        return view(p, doctor);
    }

    @Transactional
    public PrescriptionResponse updateDraft(AppUserPrincipal actor, Long id, PrescriptionRequest req) {
        Prescription p = find(id);
        Doctor doctor = requireAuthor(actor, p);
        requireDraft(p);
        checkVersion(p, req.version());
        applyContent(p, req);
        // Item rows are children, so changing only them would not bump the parent's @Version.
        // PESSIMISTIC_FORCE_INCREMENT locks the row and increments the version immediately, so stale
        // clients are always detected and the response carries the new version.
        entityManager.lock(p, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        prescriptionRepository.saveAndFlush(p);
        return view(p, doctor);
    }

    /** Deletes an unpublished draft (patients never saw it). A revision draft leaves the previous version current. */
    @Transactional
    public void discardDraft(AppUserPrincipal actor, Long id) {
        Prescription p = find(id);
        requireAuthor(actor, p);
        requireDraft(p);
        prescriptionRepository.delete(p);
        auditService.record("PRESCRIPTION_DRAFT_DISCARDED", "Prescription", id, AuditOutcome.SUCCESS, p.getPrescriptionNo());
    }

    @Transactional
    public PrescriptionResponse finalizePrescription(AppUserPrincipal actor, Long id, Long version) {
        Prescription p = find(id);
        Doctor doctor = requireAuthor(actor, p);
        requireDraft(p);
        checkVersion(p, version);
        if (p.getItems().isEmpty()) {
            throw new BusinessRuleException("NO_ITEMS", "Add at least one medicine before finalizing");
        }
        if (p.getPreviousVersionId() != null) {
            prescriptionRepository.findById(p.getPreviousVersionId()).ifPresent(Prescription::supersede);
        }
        p.finalizeVersion();
        prescriptionRepository.saveAndFlush(p);
        notificationService.notify(p.getPatient().getUser().getId(), NotificationType.PRESCRIPTION_FINALIZED,
                p.getVersionNumber() > 1 ? "Prescription updated" : "New prescription available",
                "Dr. " + stripDr(doctor.getUser().getFullName()) + " has issued prescription " + p.getPrescriptionNo()
                        + (p.getVersionNumber() > 1 ? " (version " + p.getVersionNumber() + ")" : "")
                        + ". You can view and download it in MediCare.",
                "/patient/prescriptions/" + p.getId(), "RX_FINALIZED:" + p.getId(), true);
        auditService.record("PRESCRIPTION_FINALIZED", "Prescription", id, AuditOutcome.SUCCESS,
                p.getPrescriptionNo() + " v" + p.getVersionNumber());
        return view(p, doctor);
    }

    /** Starts a new draft version of a finalized prescription. Only one revision draft may exist at a time. */
    @Transactional
    public PrescriptionResponse revise(AppUserPrincipal actor, Long id, String reason) {
        Prescription p = find(id);
        Doctor doctor = requireAuthor(actor, p);
        if (p.getStatus() != PrescriptionStatus.FINALIZED) {
            throw new BusinessRuleException("NOT_CURRENT", "Only the current finalized version can be revised");
        }
        if (prescriptionRepository.existsByPrescriptionNoAndStatus(p.getPrescriptionNo(), PrescriptionStatus.DRAFT)) {
            throw new ConflictException("REVISION_IN_PROGRESS", "A revision draft already exists for this prescription");
        }
        Prescription next = prescriptionRepository.saveAndFlush(p.newRevision(reason.trim()));
        auditService.record("PRESCRIPTION_REVISION_STARTED", "Prescription", next.getId(), AuditOutcome.SUCCESS,
                p.getPrescriptionNo() + " v" + next.getVersionNumber());
        return view(next, doctor);
    }

    // ================================================================ read

    @Transactional
    public PrescriptionResponse get(AppUserPrincipal actor, Long id) {
        Prescription p = find(id);
        if (actor.role() == Role.PATIENT) {
            requireOwnIssued(actor, p);
            return PrescriptionMapper.toResponse(p, false, false);
        }
        Doctor doctor = requireReadingDoctor(actor, p);
        return view(p, doctor);
    }

    /** All versions with this prescription's number, newest first, filtered to what the viewer may see. */
    @Transactional
    public List<PrescriptionSummary> versions(AppUserPrincipal actor, Long id) {
        Prescription p = find(id);
        boolean author;
        if (actor.role() == Role.PATIENT) {
            requireOwnIssued(actor, p);
            author = false;
        } else {
            Doctor doctor = requireReadingDoctor(actor, p);
            author = p.getDoctor().getId().equals(doctor.getId());
        }
        boolean showDrafts = author;
        return prescriptionRepository.findByPrescriptionNoOrderByVersionNumberDesc(p.getPrescriptionNo()).stream()
                .filter(v -> showDrafts || v.getStatus() != PrescriptionStatus.DRAFT)
                .map(PrescriptionMapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> forConsultation(AppUserPrincipal actor, Long consultationId) {
        Doctor doctor = accessService.requireDoctor(actor);
        Consultation c = consultationRepository.findWithDetailsById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId));
        boolean author = c.getDoctor().getId().equals(doctor.getId());
        if (!author && !accessService.hasTreatmentRelationship(doctor.getId(), c.getPatient().getId())) {
            throw new AccessDeniedException("You can only access records of patients you are treating");
        }
        return prescriptionRepository.findByConsultationIdOrderByVersionNumberDesc(consultationId).stream()
                .filter(p -> author || p.getStatus() != PrescriptionStatus.DRAFT)
                .map(p -> view(p, doctor)).toList();
    }

    /** The patient's own prescriptions; by default only current (finalized) versions. */
    @Transactional(readOnly = true)
    public PageResponse<PrescriptionSummary> listForPatient(AppUserPrincipal actor, String search, Long doctorId,
                                                            LocalDate from, LocalDate to, boolean includeSuperseded,
                                                            int page, int size) {
        Patient patient = patientRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
        Set<PrescriptionStatus> statuses = includeSuperseded ? ISSUED : EnumSet.of(PrescriptionStatus.FINALIZED);
        return search(patient.getId(), doctorId, statuses, from, to, search, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<PrescriptionSummary> listForDoctor(AppUserPrincipal actor, String search, LocalDate from,
                                                           LocalDate to, PrescriptionStatus status, int page, int size) {
        Doctor doctor = accessService.requireDoctor(actor);
        Set<PrescriptionStatus> statuses = status == null ? EnumSet.allOf(PrescriptionStatus.class) : EnumSet.of(status);
        return search(null, doctor.getId(), statuses, from, to, search, page, size);
    }

    /** PDF of a finalized or superseded version (drafts have no PDF). */
    @Transactional
    public Pdf pdf(AppUserPrincipal actor, Long id) {
        Prescription p = find(id);
        if (actor.role() == Role.PATIENT) {
            requireOwnIssued(actor, p);
            auditService.record("PRESCRIPTION_PDF_DOWNLOADED", "Prescription", id, AuditOutcome.SUCCESS, null);
        } else {
            requireReadingDoctor(actor, p);
            if (p.getStatus() == PrescriptionStatus.DRAFT) {
                throw new BusinessRuleException("DRAFT_HAS_NO_PDF", "Finalize the prescription to generate its PDF");
            }
        }
        byte[] bytes = pdfGenerator.generate(p, settingsService.getSettings(), clock.zone());
        return new Pdf(p.getPrescriptionNo() + "-v" + p.getVersionNumber() + ".pdf", bytes);
    }

    public record Pdf(String fileName, byte[] content) {
    }

    // ================================================================ helpers

    private PageResponse<PrescriptionSummary> search(Long patientId, Long doctorId, Set<PrescriptionStatus> statuses,
                                                     LocalDate from, LocalDate to, String search, int page, int size) {
        ZoneId zone = clock.zone();
        Instant fromTime = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toTime = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        if (fromTime != null && toTime != null && !toTime.isAfter(fromTime)) {
            throw new BusinessRuleException("INVALID_DATES", "'To' must be on or after 'from'");
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(prescriptionRepository.search(patientId, doctorId, statuses, fromTime, toTime, term, pageable),
                PrescriptionMapper::toSummary);
    }

    private void applyContent(Prescription p, PrescriptionRequest req) {
        List<PrescriptionItem> items = new ArrayList<>();
        int order = 0;
        for (PrescriptionItemDto i : req.items()) {
            items.add(new PrescriptionItem(p, i.medicineName().trim(), blankToNull(i.strength()), i.dosage().trim(),
                    i.frequency().trim(), i.duration().trim(), blankToNull(i.instructions()), i.quantity(), order++));
        }
        p.replaceItems(items);
        p.setGeneralInstructions(blankToNull(req.generalInstructions()));
    }

    private Doctor requireAuthor(AppUserPrincipal actor, Prescription p) {
        Doctor doctor = accessService.requireDoctor(actor);
        if (!p.getDoctor().getId().equals(doctor.getId())) {
            throw new AccessDeniedException("Only the prescribing doctor can change this prescription");
        }
        return doctor;
    }

    /** Author: any version. Other doctors: treating relationship and not a draft (audited). */
    private Doctor requireReadingDoctor(AppUserPrincipal actor, Prescription p) {
        Doctor doctor = accessService.requireDoctor(actor);
        if (p.getDoctor().getId().equals(doctor.getId())) {
            return doctor;
        }
        accessService.requireTreatingDoctor(actor, p.getPatient().getId(), "PRESCRIPTION_VIEWED");
        if (p.getStatus() == PrescriptionStatus.DRAFT) {
            throw new ResourceNotFoundException("Prescription", p.getId());
        }
        return doctor;
    }

    private static void requireOwnIssued(AppUserPrincipal actor, Prescription p) {
        if (!p.getPatient().getUser().getId().equals(actor.id()) || !ISSUED.contains(p.getStatus())) {
            throw new ResourceNotFoundException("Prescription", p.getId());
        }
    }

    private static void requireDraft(Prescription p) {
        if (p.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessRuleException("PRESCRIPTION_FINALIZED",
                    "Finalized prescriptions cannot be changed. Create a revision instead.");
        }
    }

    private static void checkVersion(Prescription p, Long version) {
        if (version != null && version != p.getVersion()) {
            throw new ConflictException("CONCURRENT_MODIFICATION", "This prescription was changed elsewhere. Reload it first.");
        }
    }

    private PrescriptionResponse view(Prescription p, Doctor viewer) {
        boolean author = p.getDoctor().getId().equals(viewer.getId());
        boolean revisable = author && p.getStatus() == PrescriptionStatus.FINALIZED
                && !prescriptionRepository.existsByPrescriptionNoAndStatus(p.getPrescriptionNo(), PrescriptionStatus.DRAFT);
        return PrescriptionMapper.toResponse(p, author && p.getStatus() == PrescriptionStatus.DRAFT, revisable);
    }

    private Prescription find(Long id) {
        return prescriptionRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Prescription", id));
    }

    private static String stripDr(String name) {
        return name.replaceFirst("^(?i)dr\\.?\\s+", "");
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
