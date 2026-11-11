package com.medicare.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.inventory.AdjustmentRequest;
import com.medicare.dto.inventory.BatchResponse;
import com.medicare.dto.inventory.InventoryAlerts;
import com.medicare.dto.inventory.MedicineDetail;
import com.medicare.dto.inventory.MedicineRequest;
import com.medicare.dto.inventory.MedicineSummary;
import com.medicare.dto.inventory.StockInRequest;
import com.medicare.dto.inventory.TransactionResponse;
import com.medicare.entity.InventoryTransaction;
import com.medicare.entity.Medicine;
import com.medicare.entity.MedicineBatch;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.InventoryTxnType;
import com.medicare.enums.MedicineCategory;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.repository.InventoryTransactionRepository;
import com.medicare.repository.MedicineBatchRepository;
import com.medicare.repository.MedicineRepository;
import com.medicare.repository.UserRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Medicine catalog and stock, managed by administrators.
 *
 * <ul>
 *   <li>Every stock change writes an {@link InventoryTransaction} with the balance after it.</li>
 *   <li>Changes lock the batch row ({@code SELECT ... FOR UPDATE}); stock never goes below zero
 *       (checked here and by a database constraint).</li>
 *   <li>Expired batches never count as usable stock and cannot receive stock.</li>
 * </ul>
 */
@Service
public class InventoryService {

    private static final Set<InventoryTxnType> MANUAL_TYPES =
            EnumSet.of(InventoryTxnType.ADJUSTMENT_IN, InventoryTxnType.ADJUSTMENT_OUT, InventoryTxnType.STOCK_OUT);

    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final HospitalClock clock;
    private final HospitalSettingsService settingsService;
    private final AuditService auditService;

    public InventoryService(MedicineRepository medicineRepository, MedicineBatchRepository batchRepository,
                            InventoryTransactionRepository transactionRepository, UserRepository userRepository,
                            HospitalClock clock, HospitalSettingsService settingsService, AuditService auditService) {
        this.medicineRepository = medicineRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    // ================================================================ catalog

    @Transactional(readOnly = true)
    public PageResponse<MedicineSummary> search(String search, MedicineCategory category, boolean includeInactive,
                                                int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        var result = medicineRepository.search(term, category, includeInactive,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name").and(Sort.by("strength"))));
        Map<Long, Totals> totals = totals(result.getContent().stream().map(Medicine::getId).toList());
        return PageResponse.from(result, m -> summary(m, totals.get(m.getId())));
    }

    @Transactional(readOnly = true)
    public MedicineDetail get(Long id) {
        Medicine m = findMedicine(id);
        LocalDate today = clock.today();
        int warn = warningDays();
        List<BatchResponse> batches = batchRepository.findByMedicineIdOrderByExpiryDateAscIdAsc(id).stream()
                .map(b -> batch(b, today, warn)).toList();
        return new MedicineDetail(summary(m, totals(List.of(id)).get(id)), batches, warn);
    }

    @Transactional
    public MedicineDetail create(MedicineRequest req) {
        String name = req.name().trim();
        if (medicineRepository.existsByNameIgnoreCaseAndStrengthIgnoreCaseAndManufacturerIgnoreCase(name,
                req.strength().trim(), req.manufacturer().trim())) {
            throw new ConflictException("MEDICINE_EXISTS", "This medicine (name, strength, manufacturer) already exists");
        }
        Medicine m = new Medicine(name);
        apply(m, req);
        medicineRepository.saveAndFlush(m);
        auditService.record("MEDICINE_CREATED", "Medicine", m.getId(), AuditOutcome.SUCCESS, name + " " + m.getStrength());
        return get(m.getId());
    }

    @Transactional
    public MedicineDetail update(Long id, MedicineRequest req) {
        Medicine m = findMedicine(id);
        if (medicineRepository.existsByNameIgnoreCaseAndStrengthIgnoreCaseAndManufacturerIgnoreCaseAndIdNot(req.name().trim(),
                req.strength().trim(), req.manufacturer().trim(), id)) {
            throw new ConflictException("MEDICINE_EXISTS", "This medicine (name, strength, manufacturer) already exists");
        }
        apply(m, req);
        medicineRepository.saveAndFlush(m);
        auditService.record("MEDICINE_UPDATED", "Medicine", id, AuditOutcome.SUCCESS, m.getName() + " " + m.getStrength());
        return get(id);
    }

    @Transactional
    public MedicineDetail setActive(Long id, boolean active) {
        Medicine m = findMedicine(id);
        m.setActive(active);
        medicineRepository.saveAndFlush(m);
        auditService.record(active ? "MEDICINE_ACTIVATED" : "MEDICINE_DEACTIVATED", "Medicine", id, AuditOutcome.SUCCESS, m.getName());
        return get(id);
    }

    // ================================================================ stock

    @Transactional
    public MedicineDetail stockIn(AppUserPrincipal actor, Long medicineId, StockInRequest req) {
        Medicine m = findMedicine(medicineId);
        if (!m.isActive()) {
            throw new BusinessRuleException("MEDICINE_INACTIVE", "Activate the medicine before receiving stock");
        }
        if (!req.expiryDate().isAfter(clock.today())) {
            throw new BusinessRuleException("BATCH_EXPIRED", "Expiry date must be after today; expired stock cannot be received");
        }
        String batchNo = req.batchNumber().trim();
        if (batchRepository.existsByMedicineIdAndBatchNumberIgnoreCase(medicineId, batchNo)) {
            throw new ConflictException("BATCH_EXISTS", "Batch " + batchNo + " is already recorded for this medicine. Use an adjustment instead.");
        }
        MedicineBatch b = batchRepository.saveAndFlush(new MedicineBatch(m, batchNo, req.expiryDate(), req.quantity(),
                req.purchasePrice(), blankToNull(req.supplier())));
        transactionRepository.save(new InventoryTransaction(b, InventoryTxnType.STOCK_IN, req.quantity(), req.quantity(),
                null, blankToNull(req.reference()), blankToNull(req.supplier()), user(actor)));
        auditService.record("STOCK_RECEIVED", "MedicineBatch", b.getId(), AuditOutcome.SUCCESS,
                m.getName() + " batch " + batchNo + " +" + req.quantity());
        return get(medicineId);
    }

    @Transactional
    public MedicineDetail adjust(AppUserPrincipal actor, Long batchId, AdjustmentRequest req) {
        if (!MANUAL_TYPES.contains(req.type())) {
            throw new BusinessRuleException("INVALID_ADJUSTMENT", "Choose ADJUSTMENT_IN, ADJUSTMENT_OUT or STOCK_OUT");
        }
        MedicineBatch b = lockBatch(batchId);
        if (req.type().isIncrease() && b.isExpired(clock.today())) {
            throw new BusinessRuleException("BATCH_EXPIRED", "Stock cannot be added to an expired batch");
        }
        int balance = b.apply(req.type().isIncrease() ? req.quantity() : -req.quantity());
        transactionRepository.save(new InventoryTransaction(b, req.type(), req.quantity(), balance, null, null,
                req.reason().trim(), user(actor)));
        auditService.record("STOCK_ADJUSTED", "MedicineBatch", batchId, AuditOutcome.SUCCESS,
                req.type() + " " + req.quantity() + " -> " + balance);
        return get(b.getMedicine().getId());
    }

    /** Removes all remaining stock of an expired batch from the usable inventory. */
    @Transactional
    public MedicineDetail writeOffExpired(AppUserPrincipal actor, Long batchId) {
        MedicineBatch b = lockBatch(batchId);
        if (!b.isExpired(clock.today())) {
            throw new BusinessRuleException("BATCH_NOT_EXPIRED", "Only expired batches can be written off this way");
        }
        int qty = b.getQuantityAvailable();
        if (qty == 0) {
            throw new BusinessRuleException("NOTHING_TO_WRITE_OFF", "This batch has no stock left");
        }
        b.apply(-qty);
        transactionRepository.save(new InventoryTransaction(b, InventoryTxnType.EXPIRED_WRITE_OFF, qty, 0, null, null,
                "Expired " + b.getExpiryDate(), user(actor)));
        auditService.record("STOCK_WRITTEN_OFF", "MedicineBatch", batchId, AuditOutcome.SUCCESS, qty + " expired");
        return get(b.getMedicine().getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> transactions(Long medicineId, InventoryTxnType type, int page, int size) {
        var result = transactionRepository.search(medicineId, type, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PageResponse.from(result, t -> new TransactionResponse(t.getId(), t.getMedicine().getId(), t.getMedicine().getName(),
                t.getMedicine().getStrength(), t.getBatch().getId(), t.getBatch().getBatchNumber(), t.getType(), t.getQuantity(),
                t.getBalanceAfter(), t.getDispensingId(), t.getReference(), t.getNotes(), t.getPerformedBy().getFullName(),
                t.getCreatedAt()));
    }

    // ================================================================ alerts

    @Transactional(readOnly = true)
    public InventoryAlerts alerts() {
        LocalDate today = clock.today();
        int warn = warningDays();
        List<Medicine> active = medicineRepository.findByActiveTrueOrderByNameAsc();
        Map<Long, Totals> totals = totals(active.stream().map(Medicine::getId).toList());
        List<MedicineSummary> low = active.stream().map(m -> summary(m, totals.get(m.getId())))
                .filter(MedicineSummary::lowStock)
                .sorted(Comparator.comparingInt(MedicineSummary::availableQuantity)).toList();
        List<InventoryAlerts.AlertBatch> soon = batchRepository.findExpiringBetween(today, today.plusDays(warn)).stream()
                .filter(b -> b.getMedicine().isActive()).map(b -> alertBatch(b, today)).toList();
        List<InventoryAlerts.AlertBatch> expired = batchRepository.findExpiredWithStock(today).stream()
                .map(b -> alertBatch(b, today)).toList();
        return new InventoryAlerts(warn, low, soon, expired);
    }

    // ================================================================ helpers

    record Totals(int usable, int expired, LocalDate nearestExpiry) {
        static final Totals NONE = new Totals(0, 0, null);
    }

    Map<Long, Totals> totals(Collection<Long> ids) {
        Map<Long, Totals> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Object[] row : batchRepository.stockTotals(ids, clock.today())) {
            map.put((Long) row[0], new Totals(((Number) row[1]).intValue(), ((Number) row[2]).intValue(), (LocalDate) row[3]));
        }
        return map;
    }

    static MedicineSummary summary(Medicine m, Totals t) {
        Totals totals = t == null ? Totals.NONE : t;
        return new MedicineSummary(m.getId(), m.getName(), m.getGenericName(), m.getManufacturer(), m.getCategory(),
                m.getStrength(), m.getUnit(), m.getUnitPrice(), m.getReorderLevel(), m.isActive(), totals.usable(),
                totals.expired(), totals.nearestExpiry(), m.isActive() && totals.usable() <= m.getReorderLevel());
    }

    private static BatchResponse batch(MedicineBatch b, LocalDate today, int warn) {
        boolean expired = b.isExpired(today);
        return new BatchResponse(b.getId(), b.getBatchNumber(), b.getExpiryDate(), b.getQuantityReceived(),
                b.getQuantityAvailable(), b.getPurchasePrice(), b.getSupplier(), b.getReceivedAt(), expired,
                !expired && !b.getExpiryDate().isAfter(today.plusDays(warn)));
    }

    private static InventoryAlerts.AlertBatch alertBatch(MedicineBatch b, LocalDate today) {
        Medicine m = b.getMedicine();
        return new InventoryAlerts.AlertBatch(b.getId(), m.getId(), m.getName(), m.getStrength(), m.getUnit(),
                b.getBatchNumber(), b.getExpiryDate(), b.getQuantityAvailable(), ChronoUnit.DAYS.between(today, b.getExpiryDate()));
    }

    private static void apply(Medicine m, MedicineRequest req) {
        m.update(req.name().trim(), blankToNull(req.genericName()), req.manufacturer().trim(), req.category(),
                req.strength().trim(), req.unit().trim(), req.unitPrice(), req.reorderLevel());
    }

    private MedicineBatch lockBatch(Long id) {
        return batchRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Batch", id));
    }

    private Medicine findMedicine(Long id) {
        return medicineRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Medicine", id));
    }

    private User user(AppUserPrincipal actor) {
        return userRepository.getReferenceById(actor.id());
    }

    int warningDays() {
        return settingsService.getSettings().getExpiryWarningDays();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
