package com.medicare.service;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.lab.CreateLabOrderRequest;
import com.medicare.dto.lab.LabOrderResponse;
import com.medicare.dto.lab.LabOrderStatusRequest;
import com.medicare.dto.lab.LabOrderSummary;
import com.medicare.dto.lab.LabResultsRequest;
import com.medicare.entity.Consultation;
import com.medicare.entity.Doctor;
import com.medicare.entity.LabOrder;
import com.medicare.entity.LabOrderItem;
import com.medicare.entity.LabReport;
import com.medicare.entity.LabTest;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.LabOrderStatus;
import com.medicare.enums.LabPriority;
import com.medicare.enums.NotificationType;
import com.medicare.enums.Role;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.LabMapper;
import com.medicare.mapper.LabMapper.Viewer;
import com.medicare.notification.NotificationService;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.LabOrderRepository;
import com.medicare.repository.LabReportRepository;
import com.medicare.repository.LabTestRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.UserRepository;
import com.medicare.security.AppUserPrincipal;
import com.medicare.storage.LabReportStorage;
import com.medicare.storage.PdfUploadValidator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Laboratory orders in the four-role model.
 *
 * <ul>
 *   <li>Only the doctor of a consultation orders tests for it, moves the order through its statuses,
 *       types result values, uploads the PDF report and finalizes it.</li>
 *   <li>Other doctors with a treatment relationship can read completed results (audited).</li>
 *   <li>Patients see their own orders; results and the report only after finalization.</li>
 *   <li>Receptionists and administrators cannot read orders or reports (denials are audited).
 *       Administrators only maintain the catalog ({@link LabCatalogService}).</li>
 *   <li>The system never interprets results: values and comments are stored exactly as typed.</li>
 * </ul>
 */
@Service
public class LabOrderService {

    private static final Logger log = LoggerFactory.getLogger(LabOrderService.class);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LabOrderRepository orderRepository;
    private final LabTestRepository testRepository;
    private final LabReportRepository reportRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ClinicalAccessService accessService;
    private final SequenceService sequenceService;
    private final HospitalClock clock;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final LabReportStorage storage;
    private final EntityManager entityManager;

    public LabOrderService(LabOrderRepository orderRepository, LabTestRepository testRepository,
                           LabReportRepository reportRepository, ConsultationRepository consultationRepository,
                           PatientRepository patientRepository, UserRepository userRepository,
                           ClinicalAccessService accessService, SequenceService sequenceService, HospitalClock clock,
                           NotificationService notificationService, AuditService auditService,
                           LabReportStorage storage, EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.testRepository = testRepository;
        this.reportRepository = reportRepository;
        this.consultationRepository = consultationRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.sequenceService = sequenceService;
        this.clock = clock;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.storage = storage;
        this.entityManager = entityManager;
    }

    // ================================================================ ordering doctor: write

    @Transactional
    public LabOrderResponse create(AppUserPrincipal actor, CreateLabOrderRequest req) {
        Doctor doctor = accessService.requireDoctor(actor);
        Consultation consultation = consultationRepository.findWithDetailsById(req.consultationId())
                .filter(c -> c.getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", req.consultationId()));
        if (consultation.getAppointment().getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("APPOINTMENT_CANCELLED", "Cannot order tests for a cancelled appointment");
        }
        Set<Long> ids = new LinkedHashSet<>(req.testIds());
        if (ids.size() != req.testIds().size()) {
            throw new BusinessRuleException("DUPLICATE_TEST", "Each test can be ordered only once per order");
        }
        Map<Long, LabTest> found = new HashMap<>();
        testRepository.findAllById(ids).forEach(t -> found.put(t.getId(), t));
        String number = "LAB-" + NO_DATE.format(clock.today()) + "-"
                + String.format("%06d", sequenceService.next(SequenceService.LAB_ORDER));
        LabOrder order = new LabOrder(number, consultation,
                req.priority() == null ? LabPriority.ROUTINE : req.priority(), blankToNull(req.clinicalNotes()));
        for (Long id : ids) {
            LabTest t = found.get(id);
            if (t == null) {
                throw new ResourceNotFoundException("Lab test", id);
            }
            if (!t.isActive()) {
                throw new BusinessRuleException("LAB_TEST_INACTIVE", t.getName() + " is no longer offered");
            }
            order.addItem(t);
        }
        orderRepository.saveAndFlush(order);
        auditService.record("LAB_ORDER_CREATED", "LabOrder", order.getId(), AuditOutcome.SUCCESS,
                number + " (" + ids.size() + " test(s))");
        return LabMapper.toResponse(order, Viewer.ORDERING_DOCTOR);
    }

    @Transactional
    public LabOrderResponse changeStatus(AppUserPrincipal actor, Long id, LabOrderStatusRequest req) {
        LabOrder o = lockOwned(actor, id);
        checkVersion(o, req.version());
        if (!o.getStatus().manualTargets().contains(req.status())) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Cannot change a " + label(o.getStatus()) + " order to " + label(req.status()));
        }
        String reason = blankToNull(req.reason());
        if (req.status() == LabOrderStatus.CANCELLED && (reason == null || reason.length() < 3)) {
            throw new BusinessRuleException("REASON_REQUIRED", "Enter a reason for cancelling (at least 3 characters)");
        }
        LabOrderStatus from = o.getStatus();
        o.changeStatus(req.status(), reason);
        orderRepository.saveAndFlush(o);
        auditService.record("LAB_ORDER_STATUS_CHANGED", "LabOrder", id, AuditOutcome.SUCCESS, from + " -> " + req.status());
        return LabMapper.toResponse(o, Viewer.ORDERING_DOCTOR);
    }

    @Transactional
    public LabOrderResponse saveResults(AppUserPrincipal actor, Long id, LabResultsRequest req) {
        LabOrder o = lockOwned(actor, id);
        checkVersion(o, req.version());
        requireSampleCollected(o);
        Map<Long, LabOrderItem> items = new HashMap<>();
        o.getItems().forEach(i -> items.put(i.getId(), i));
        Set<Long> seen = new LinkedHashSet<>();
        for (LabResultsRequest.Entry e : req.items()) {
            LabOrderItem item = items.get(e.itemId());
            if (item == null || !seen.add(e.itemId())) {
                throw new BusinessRuleException("INVALID_RESULT_ITEM", "A result does not belong to this order");
            }
            item.recordResult(blankToNull(e.resultValue()), blankToNull(e.resultComment()));
        }
        bumpVersion(o);
        auditService.record("LAB_RESULTS_SAVED", "LabOrder", id, AuditOutcome.SUCCESS, null);
        return LabMapper.toResponse(o, Viewer.ORDERING_DOCTOR);
    }

    /**
     * Stores a validated PDF and links it to the order, replacing an earlier unfinalized upload.
     * The new file is removed again if the transaction rolls back; a replaced file is removed after commit.
     */
    @Transactional
    public LabOrderResponse uploadReport(AppUserPrincipal actor, Long id, Long version, String originalFilename, byte[] content) {
        LabOrder o = lockOwned(actor, id);
        checkVersion(o, version);
        requireSampleCollected(o);
        PdfUploadValidator.validate(originalFilename, content);
        User uploader = userRepository.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", actor.id()));

        LabReportStorage.StoredFile stored = storage.store(content);
        afterCompletion(stored.storedName(), null);
        LabReport report = o.getReport();
        String replaced = null;
        if (report == null) {
            report = new LabReport(o, uploader);
            o.attachReport(report);
        } else {
            replaced = report.getStoredFilename();
        }
        report.replaceFile(uploader, PdfUploadValidator.safeDisplayName(originalFilename), stored.storedName(),
                stored.size(), stored.sha256());
        reportRepository.save(report);
        if (replaced != null) {
            afterCompletion(null, replaced);
        }
        if (o.getStatus() == LabOrderStatus.SAMPLE_COLLECTED) {
            o.changeStatus(LabOrderStatus.PROCESSING, null);
        }
        bumpVersion(o);
        auditService.record(replaced == null ? "LAB_REPORT_UPLOADED" : "LAB_REPORT_REPLACED", "LabOrder", id,
                AuditOutcome.SUCCESS, stored.size() + " bytes, sha256 " + stored.sha256().substring(0, 12));
        return LabMapper.toResponse(o, Viewer.ORDERING_DOCTOR);
    }

    @Transactional
    public LabOrderResponse removeReport(AppUserPrincipal actor, Long id, Long version) {
        LabOrder o = lockOwned(actor, id);
        checkVersion(o, version);
        LabReport report = o.getReport();
        if (report == null) {
            throw new ResourceNotFoundException("No report has been uploaded for this order");
        }
        String file = report.getStoredFilename();
        o.attachReport(null);
        reportRepository.delete(report);
        afterCompletion(null, file);
        bumpVersion(o);
        auditService.record("LAB_REPORT_REMOVED", "LabOrder", id, AuditOutcome.SUCCESS, null);
        return LabMapper.toResponse(o, Viewer.ORDERING_DOCTOR);
    }

    /** Releases the report to the patient. Finalized reports and results can no longer be changed. */
    @Transactional
    public LabOrderResponse finalizeReport(AppUserPrincipal actor, Long id, Long version) {
        LabOrder o = lockOwned(actor, id);
        Doctor doctor = o.getDoctor();
        checkVersion(o, version);
        if (o.getStatus() != LabOrderStatus.PROCESSING || o.getReport() == null) {
            throw new BusinessRuleException("REPORT_REQUIRED", "Upload the PDF report before finalizing");
        }
        o.getReport().finalizeReport();
        o.changeStatus(LabOrderStatus.COMPLETED, null);
        orderRepository.saveAndFlush(o);
        notificationService.notify(o.getPatient().getUser().getId(), NotificationType.LAB_REPORT_READY,
                "Lab report ready",
                "Your lab report for order " + o.getOrderNo() + " from Dr. " + stripDr(doctor.getUser().getFullName())
                        + " is ready. You can view and download it in MediCare.",
                "/patient/lab-reports/" + o.getId(), "LAB_READY:" + o.getId(), true);
        auditService.record("LAB_REPORT_FINALIZED", "LabOrder", id, AuditOutcome.SUCCESS, o.getOrderNo());
        return LabMapper.toResponse(o, Viewer.ORDERING_DOCTOR);
    }

    // ================================================================ read

    @Transactional
    public LabOrderResponse get(AppUserPrincipal actor, Long id) {
        denyOtherRoles(actor, id);
        LabOrder o = find(id);
        Viewer viewer = authorizeRead(actor, o, "LAB_ORDER_VIEWED");
        if (viewer == Viewer.PATIENT) {
            auditService.record("LAB_ORDER_VIEWED", "LabOrder", id, AuditOutcome.SUCCESS, null);
        }
        return LabMapper.toResponse(o, viewer);
    }

    @Transactional
    public List<LabOrderSummary> forConsultation(AppUserPrincipal actor, Long consultationId) {
        Doctor doctor = accessService.requireDoctor(actor);
        Consultation c = consultationRepository.findWithDetailsById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId));
        if (!c.getDoctor().getId().equals(doctor.getId())) {
            accessService.requireTreatingDoctor(actor, c.getPatient().getId(), "LAB_RESULTS_VIEWED");
        }
        return orderRepository.findByConsultationIdOrderByOrderedAtDesc(consultationId).stream()
                .map(o -> LabMapper.toSummary(o, viewerFor(doctor, o))).toList();
    }

    /** All orders of a patient the doctor is treating (results are opened per order). */
    @Transactional
    public PageResponse<LabOrderSummary> forPatientOfDoctor(AppUserPrincipal actor, Long patientId, int page, int size) {
        Doctor doctor = accessService.requireTreatingDoctor(actor, patientId, "LAB_RESULTS_VIEWED");
        return PageResponse.from(orderRepository.search(patientId, null, EnumSet.allOf(LabOrderStatus.class), null,
                pageable(page, size)), o -> LabMapper.toSummary(o, viewerFor(doctor, o)));
    }

    @Transactional(readOnly = true)
    public PageResponse<LabOrderSummary> listForDoctor(AppUserPrincipal actor, LabOrderStatus status, String search,
                                                       int page, int size) {
        Doctor doctor = accessService.requireDoctor(actor);
        Set<LabOrderStatus> statuses = status == null ? EnumSet.allOf(LabOrderStatus.class) : EnumSet.of(status);
        return PageResponse.from(orderRepository.search(null, doctor.getId(), statuses, term(search), pageable(page, size)),
                o -> LabMapper.toSummary(o, Viewer.ORDERING_DOCTOR));
    }

    @Transactional(readOnly = true)
    public PageResponse<LabOrderSummary> listForPatient(AppUserPrincipal actor, LabOrderStatus status, String search,
                                                        int page, int size) {
        Patient patient = patientRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
        Set<LabOrderStatus> statuses = status == null ? EnumSet.allOf(LabOrderStatus.class) : EnumSet.of(status);
        return PageResponse.from(orderRepository.search(patient.getId(), null, statuses, term(search), pageable(page, size)),
                o -> LabMapper.toSummary(o, Viewer.PATIENT));
    }

    /** The stored PDF. Its SHA-256 is re-checked so a modified or corrupted file is never served. */
    @Transactional
    public Download download(AppUserPrincipal actor, Long id) {
        denyOtherRoles(actor, id);
        LabOrder o = find(id);
        Viewer viewer = authorizeRead(actor, o, "LAB_REPORT_VIEWED");
        LabReport r = o.getReport();
        if (r == null || (viewer != Viewer.ORDERING_DOCTOR && !r.isFinalized())) {
            throw new ResourceNotFoundException("No report is available for this order yet");
        }
        byte[] bytes = storage.read(r.getStoredFilename());
        if (!LabReportStorage.sha256(bytes).equals(r.getSha256())) {
            log.error("Lab report file failed its integrity check (order id {})", id);
            auditService.recordIndependently("LAB_REPORT_INTEGRITY_FAILED", "LabOrder", id, AuditOutcome.FAILURE, null);
            throw new IllegalStateException("Report integrity check failed");
        }
        auditService.record("LAB_REPORT_DOWNLOADED", "LabOrder", id, AuditOutcome.SUCCESS, viewer.name());
        return new Download(o.getOrderNo() + (r.isFinalized() ? "" : "-draft") + ".pdf", bytes);
    }

    public record Download(String fileName, byte[] content) {
    }

    // ================================================================ helpers

    /** Receptionists and administrators never read lab orders; the attempt is audited. */
    private void denyOtherRoles(AppUserPrincipal actor, Long id) {
        if (actor.role() != Role.DOCTOR && actor.role() != Role.PATIENT) {
            auditService.recordIndependently("LAB_ACCESS_DENIED", "LabOrder", id, AuditOutcome.DENIED, "Role " + actor.role());
            throw new AccessDeniedException("Lab results are visible only to the patient and their doctors");
        }
    }

    private Viewer authorizeRead(AppUserPrincipal actor, LabOrder o, String action) {
        if (actor.role() == Role.PATIENT) {
            if (!o.getPatient().getUser().getId().equals(actor.id())) {
                throw new ResourceNotFoundException("Lab order", o.getId());
            }
            return Viewer.PATIENT;
        }
        Doctor doctor = accessService.requireDoctor(actor);
        if (o.getDoctor().getId().equals(doctor.getId())) {
            return Viewer.ORDERING_DOCTOR;
        }
        accessService.requireTreatingDoctor(actor, o.getPatient().getId(), action);
        return Viewer.TREATING_DOCTOR;
    }

    private static Viewer viewerFor(Doctor doctor, LabOrder o) {
        return o.getDoctor().getId().equals(doctor.getId()) ? Viewer.ORDERING_DOCTOR : Viewer.TREATING_DOCTOR;
    }

    /** Locks the order row, then loads it with details; only the ordering doctor may change it. */
    private LabOrder lockOwned(AppUserPrincipal actor, Long id) {
        Doctor doctor = accessService.requireDoctor(actor);
        orderRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Lab order", id));
        LabOrder o = find(id);
        if (!o.getDoctor().getId().equals(doctor.getId())) {
            auditService.recordIndependently("LAB_ACCESS_DENIED", "LabOrder", id, AuditOutcome.DENIED, "Not the ordering doctor");
            throw new AccessDeniedException("Only the ordering doctor can change this order");
        }
        if (!o.getStatus().isOpen()) {
            throw new BusinessRuleException("ORDER_CLOSED",
                    "This order is " + label(o.getStatus()) + " and can no longer be changed");
        }
        return o;
    }

    private static void requireSampleCollected(LabOrder o) {
        if (!o.getStatus().acceptsReport()) {
            throw new BusinessRuleException("SAMPLE_NOT_COLLECTED", "Mark the sample as collected first");
        }
    }

    private static void checkVersion(LabOrder o, Long version) {
        if (version != null && version != o.getVersion()) {
            throw new ConflictException("CONCURRENT_MODIFICATION", "This order was changed elsewhere. Reload it first.");
        }
    }

    /** Child rows (results, report) do not touch the order row, so its version is incremented explicitly. */
    private void bumpVersion(LabOrder o) {
        entityManager.lock(o, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        orderRepository.saveAndFlush(o);
    }

    /** Deletes {@code onRollback} if the transaction fails and {@code onCommit} once it has committed. */
    private void afterCompletion(String onRollback, String onCommit) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED && onCommit != null) {
                    storage.delete(onCommit);
                } else if (status != STATUS_COMMITTED && onRollback != null) {
                    storage.delete(onRollback);
                }
            }
        });
    }

    private LabOrder find(Long id) {
        return orderRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Lab order", id));
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "orderedAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private static String term(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }

    private static String label(LabOrderStatus s) {
        return s.name().toLowerCase().replace('_', ' ');
    }

    private static String stripDr(String name) {
        return name.replaceFirst("^(?i)dr\\.?\\s+", "");
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
