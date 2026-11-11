package com.medicare.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.inventory.DispenseRequest;
import com.medicare.dto.inventory.DispensingPrescriptionView;
import com.medicare.dto.inventory.DispensingResponse;
import com.medicare.dto.inventory.DispensingSummary;
import com.medicare.entity.DispensingItem;
import com.medicare.entity.DispensingRecord;
import com.medicare.entity.InventoryTransaction;
import com.medicare.entity.Medicine;
import com.medicare.entity.MedicineBatch;
import com.medicare.entity.Prescription;
import com.medicare.entity.PrescriptionItem;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.InventoryTxnType;
import com.medicare.enums.PrescriptionStatus;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.repository.DispensingRecordRepository;
import com.medicare.repository.InventoryTransactionRepository;
import com.medicare.repository.MedicineBatchRepository;
import com.medicare.repository.MedicineRepository;
import com.medicare.repository.PrescriptionRepository;
import com.medicare.repository.UserRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispensing medicines against a FINALIZED prescription (administrators).
 *
 * <ul>
 *   <li>The prescription is looked up by its number; only medicine lines and quantities are shown
 *       (no diagnosis or notes). The lookup is audited.</li>
 *   <li>Only the prescribed medicine and strength can be given ({@link MedicineMatcher}), only from
 *       non-expired batches, never more than prescribed, and never more than in stock.</li>
 *   <li>The prescription row and every batch involved are locked (batches in id order, to avoid
 *       deadlocks), so concurrent dispensings cannot oversell a batch or a prescription line.</li>
 * </ul>
 */
@Service
public class DispensingService {

    /** A line without a prescribed quantity can be dispensed once, up to this many units. */
    static final int MAX_UNSPECIFIED_QUANTITY = 1000;
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PrescriptionRepository prescriptionRepository;
    private final DispensingRecordRepository recordRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SequenceService sequenceService;
    private final HospitalClock clock;
    private final AuditService auditService;

    public DispensingService(PrescriptionRepository prescriptionRepository, DispensingRecordRepository recordRepository,
                             MedicineRepository medicineRepository, MedicineBatchRepository batchRepository,
                             InventoryTransactionRepository transactionRepository, UserRepository userRepository,
                             SequenceService sequenceService, HospitalClock clock, AuditService auditService) {
        this.prescriptionRepository = prescriptionRepository;
        this.recordRepository = recordRepository;
        this.medicineRepository = medicineRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.sequenceService = sequenceService;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional
    public DispensingPrescriptionView lookup(String prescriptionNo) {
        String no = prescriptionNo == null ? "" : prescriptionNo.trim();
        Prescription p = prescriptionRepository.findFirstByPrescriptionNoIgnoreCaseAndStatus(no, PrescriptionStatus.FINALIZED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No finalized prescription with number " + no + ". Drafts and replaced versions cannot be dispensed."));
        auditService.record("PRESCRIPTION_VIEWED_FOR_DISPENSING", "Prescription", p.getId(), AuditOutcome.SUCCESS, p.getPrescriptionNo());
        return view(p);
    }

    @Transactional
    public DispensingResponse dispense(AppUserPrincipal actor, DispenseRequest req) {
        Prescription p = prescriptionRepository.lockById(req.prescriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", req.prescriptionId()));
        if (p.getStatus() != PrescriptionStatus.FINALIZED) {
            throw new BusinessRuleException("PRESCRIPTION_NOT_FINALIZED", "Only the current finalized prescription can be dispensed");
        }
        Map<Long, PrescriptionItem> items = new HashMap<>();
        p.getItems().forEach(i -> items.put(i.getId(), i));
        Map<Long, Integer> already = dispensedPerItem(p.getId());

        // lock every batch once, in id order
        Map<Long, MedicineBatch> batches = new TreeMap<>();
        req.lines().stream().map(DispenseRequest.Line::batchId).sorted().distinct().forEach(id ->
                batches.put(id, batchRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Batch", id))));

        LocalDate today = clock.today();
        Map<Long, Integer> requestedPerItem = new HashMap<>();
        for (DispenseRequest.Line line : req.lines()) {
            PrescriptionItem item = items.get(line.prescriptionItemId());
            if (item == null) {
                throw new BusinessRuleException("INVALID_LINE", "A line does not belong to this prescription");
            }
            MedicineBatch batch = batches.get(line.batchId());
            Medicine m = batch.getMedicine();
            if (!m.isActive()) {
                throw new BusinessRuleException("MEDICINE_INACTIVE", m.getName() + " is inactive");
            }
            if (!MedicineMatcher.matches(item.getMedicineName(), item.getStrength(), m)) {
                throw new BusinessRuleException("MEDICINE_MISMATCH", m.getName() + " " + m.getStrength()
                        + " does not match the prescribed " + item.getMedicineName()
                        + (item.getStrength() == null ? "" : " " + item.getStrength())
                        + ". A substitution needs a revised prescription from the doctor.");
            }
            if (batch.isExpired(today)) {
                throw new BusinessRuleException("BATCH_EXPIRED", "Batch " + batch.getBatchNumber() + " expired on " + batch.getExpiryDate());
            }
            requestedPerItem.merge(item.getId(), line.quantity(), Integer::sum);
        }
        for (var e : requestedPerItem.entrySet()) {
            PrescriptionItem item = items.get(e.getKey());
            int done = already.getOrDefault(item.getId(), 0);
            Integer remaining = remaining(item, done);
            int limit = remaining == null ? MAX_UNSPECIFIED_QUANTITY : remaining;
            if ((remaining != null && remaining == 0) || (remaining == null && done > 0)) {
                throw new BusinessRuleException("ALREADY_DISPENSED", item.getMedicineName() + " has already been dispensed");
            }
            if (e.getValue() > limit) {
                throw new BusinessRuleException("QUANTITY_EXCEEDS_PRESCRIPTION", "Only " + limit + " of "
                        + item.getMedicineName() + " can still be dispensed");
            }
        }

        User user = userRepository.getReferenceById(actor.id());
        String number = "DSP-" + NO_DATE.format(today) + "-" + String.format("%06d", sequenceService.next(SequenceService.DISPENSING));
        DispensingRecord record = new DispensingRecord(number, p, user, blankToNull(req.notes()));
        for (DispenseRequest.Line line : req.lines()) {
            record.addItem(items.get(line.prescriptionItemId()), batches.get(line.batchId()), line.quantity());
        }
        recordRepository.saveAndFlush(record);
        for (DispenseRequest.Line line : req.lines()) {
            MedicineBatch batch = batches.get(line.batchId());
            int balance = batch.apply(-line.quantity());
            transactionRepository.save(new InventoryTransaction(batch, InventoryTxnType.DISPENSE, line.quantity(), balance,
                    record.getId(), number, p.getPrescriptionNo(), user));
        }
        batchRepository.flush();
        auditService.record("DISPENSING_RECORDED", "DispensingRecord", record.getId(), AuditOutcome.SUCCESS,
                number + " for " + p.getPrescriptionNo());
        return response(record);
    }

    @Transactional(readOnly = true)
    public DispensingResponse get(Long id) {
        return response(recordRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Dispensing record", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<DispensingSummary> list(String search, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(recordRepository.search(term, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "dispensedAt").and(Sort.by(Sort.Direction.DESC, "id")))), DispensingService::summary);
    }

    // ---------------------------------------------------------------- helpers

    private DispensingPrescriptionView view(Prescription p) {
        Map<Long, Integer> already = dispensedPerItem(p.getId());
        List<Medicine> active = medicineRepository.findByActiveTrueOrderByNameAsc();
        LocalDate today = clock.today();
        List<DispensingPrescriptionView.Line> lines = new ArrayList<>();
        for (PrescriptionItem item : p.getItems().stream().sorted(Comparator.comparingInt(PrescriptionItem::getSortOrder)).toList()) {
            List<Medicine> matching = active.stream()
                    .filter(m -> MedicineMatcher.matches(item.getMedicineName(), item.getStrength(), m)).toList();
            Map<Long, List<DispensingPrescriptionView.CandidateBatch>> usable = new HashMap<>();
            if (!matching.isEmpty()) {
                batchRepository.findUsable(matching.stream().map(Medicine::getId).toList(), today).forEach(b ->
                        usable.computeIfAbsent(b.getMedicine().getId(), k -> new ArrayList<>()).add(
                                new DispensingPrescriptionView.CandidateBatch(b.getId(), b.getBatchNumber(), b.getExpiryDate(),
                                        b.getQuantityAvailable())));
            }
            List<DispensingPrescriptionView.Candidate> candidates = matching.stream()
                    .map(m -> new DispensingPrescriptionView.Candidate(m.getId(), m.getName(), m.getStrength(), m.getUnit(),
                            m.getUnitPrice(), usable.getOrDefault(m.getId(), List.of())))
                    .toList();
            int done = already.getOrDefault(item.getId(), 0);
            Integer remaining = remaining(item, done);
            boolean full = remaining == null ? done > 0 : remaining == 0;
            lines.add(new DispensingPrescriptionView.Line(item.getId(), item.getMedicineName(), item.getStrength(),
                    item.getQuantity(), done, remaining, full, candidates));
        }
        List<DispensingSummary> previous = recordRepository.findByPrescriptionIdOrderByDispensedAtDesc(p.getId()).stream()
                .map(DispensingService::summary).toList();
        return new DispensingPrescriptionView(p.getId(), p.getPrescriptionNo(), p.getVersionNumber(), p.getFinalizedAt(),
                p.getPatient().getUser().getFullName(), p.getPatient().getPatientCode(), p.getDoctor().getUser().getFullName(),
                lines, previous);
    }

    private static Integer remaining(PrescriptionItem item, int done) {
        return item.getQuantity() == null ? null : Math.max(0, item.getQuantity() - done);
    }

    private Map<Long, Integer> dispensedPerItem(Long prescriptionId) {
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : recordRepository.dispensedPerItem(prescriptionId)) {
            map.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private static DispensingSummary summary(DispensingRecord r) {
        return new DispensingSummary(r.getId(), r.getDispensingNo(), r.getPrescription().getPrescriptionNo(),
                r.getPatient().getUser().getFullName(), r.getPatient().getPatientCode(), r.getDispensedBy().getFullName(),
                r.getDispensedAt(), r.getItems().size(), r.getItems().stream().mapToInt(DispensingItem::getQuantity).sum());
    }

    private static DispensingResponse response(DispensingRecord r) {
        Prescription p = r.getPrescription();
        return new DispensingResponse(r.getId(), r.getDispensingNo(), p.getId(), p.getPrescriptionNo(),
                r.getPatient().getUser().getFullName(), r.getPatient().getPatientCode(), p.getDoctor().getUser().getFullName(),
                r.getDispensedBy().getFullName(), r.getDispensedAt(), r.getNotes(),
                r.getItems().stream().map(i -> new DispensingResponse.Item(
                        i.getPrescriptionItem().getMedicineName(), i.getMedicine().getName(), i.getMedicine().getStrength(),
                        i.getMedicine().getUnit(), i.getBatch().getBatchNumber(), i.getBatch().getExpiryDate(), i.getQuantity(),
                        i.getMedicine().getUnitPrice())).toList());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
