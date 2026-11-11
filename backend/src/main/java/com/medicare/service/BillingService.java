package com.medicare.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.medicare.dto.billing.BillableCharge;
import com.medicare.dto.billing.BillableCharges;
import com.medicare.dto.billing.BillingSummary;
import com.medicare.dto.billing.CreateInvoiceRequest;
import com.medicare.dto.billing.InvoiceResponse;
import com.medicare.dto.billing.InvoiceSummary;
import com.medicare.dto.billing.PaymentRequest;
import com.medicare.dto.common.PageResponse;
import com.medicare.entity.Appointment;
import com.medicare.entity.DispensingItem;
import com.medicare.entity.HospitalSettings;
import com.medicare.entity.Invoice;
import com.medicare.entity.InvoiceItem;
import com.medicare.entity.LabOrderItem;
import com.medicare.entity.Patient;
import com.medicare.entity.Payment;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.InvoiceItemType;
import com.medicare.enums.InvoiceStatus;
import com.medicare.enums.NotificationType;
import com.medicare.enums.PaymentMethod;
import com.medicare.enums.PaymentType;
import com.medicare.enums.Role;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.notification.NotificationService;
import com.medicare.reporting.InvoicePdfGenerator;
import com.medicare.repository.BillableSourceRepository;
import com.medicare.repository.InvoiceItemRepository;
import com.medicare.repository.InvoiceRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.PaymentRepository;
import com.medicare.repository.UserRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoices and payments (receptionists write; administrators read; patients read their own).
 *
 * <ul>
 *   <li>Invoices are built from services the patient received: consultations (checked in or later), lab tests
 *       of non-cancelled orders and dispensed medicines, at the price recorded when booked/ordered/dispensed.
 *       The client only names the charges; prices always come from the server.</li>
 *   <li>Each charge is billed once (unique {@code billed_reference}); cancelling an unpaid invoice releases it.</li>
 *   <li>Payments never exceed the balance, refunds never exceed what was paid, and the invoice row is locked
 *       while money is recorded. Payments and refunds are never edited or deleted.</li>
 *   <li>No card data is stored: a reference that looks like a card number is rejected.</li>
 * </ul>
 */
@Service
public class BillingService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final Set<AppointmentStatus> BILLABLE_VISITS =
            EnumSet.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);
    private static final Set<InvoiceStatus> PAYABLE = EnumSet.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID);
    /** 13 or more digits in a row (after removing spaces and dashes) looks like a payment card number. */
    private static final Pattern CARD_LIKE = Pattern.compile("\\d{13,}");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository itemRepository;
    private final PaymentRepository paymentRepository;
    private final BillableSourceRepository sourceRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final SequenceService sequenceService;
    private final HospitalSettingsService settingsService;
    private final HospitalClock clock;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final InvoicePdfGenerator pdfGenerator;

    public BillingService(InvoiceRepository invoiceRepository, InvoiceItemRepository itemRepository,
                          PaymentRepository paymentRepository, BillableSourceRepository sourceRepository,
                          PatientRepository patientRepository, UserRepository userRepository, SequenceService sequenceService,
                          HospitalSettingsService settingsService, HospitalClock clock, NotificationService notificationService,
                          AuditService auditService, InvoicePdfGenerator pdfGenerator) {
        this.invoiceRepository = invoiceRepository;
        this.itemRepository = itemRepository;
        this.paymentRepository = paymentRepository;
        this.sourceRepository = sourceRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.sequenceService = sequenceService;
        this.settingsService = settingsService;
        this.clock = clock;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.pdfGenerator = pdfGenerator;
    }

    // ================================================================ charges and invoices

    @Transactional(readOnly = true)
    public BillableCharges billable(Long patientId) {
        Patient p = patientRepository.findById(patientId).orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        return new BillableCharges(p.getId(), p.getUser().getFullName(), p.getPatientCode(),
                settingsService.getSettings().getTaxRatePercent(), invoiceRepository.outstandingForPatient(patientId),
                List.copyOf(unbilled(patientId).values()));
    }

    @Transactional
    public InvoiceResponse create(AppUserPrincipal actor, CreateInvoiceRequest req) {
        Patient patient = patientRepository.lockById(req.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", req.patientId()));
        Map<String, BillableCharge> available = unbilled(patient.getId());
        Set<String> billed = new HashSet<>(itemRepository.billedReferences(patient.getId()));
        HospitalSettings settings = settingsService.getSettings();
        User user = userRepository.getReferenceById(actor.id());

        String number = settings.getInvoicePrefix() + "-" + NO_DATE.format(clock.today()) + "-"
                + String.format("%06d", sequenceService.next(SequenceService.INVOICE));
        Invoice invoice = new Invoice(number, patient, user, blankToNull(req.notes()));
        Set<String> seen = new HashSet<>();
        for (CreateInvoiceRequest.ChargeRef ref : nonNull(req.charges())) {
            String key = InvoiceItem.key(ref.type(), ref.referenceId());
            if (!seen.add(key)) {
                throw new BusinessRuleException("DUPLICATE_CHARGE", "A charge was selected twice");
            }
            BillableCharge c = available.get(key);
            if (c == null) {
                if (billed.contains(key)) {
                    throw new ConflictException("ALREADY_BILLED", "One of the selected charges is already on another invoice. Reload the list.");
                }
                throw new BusinessRuleException("CHARGE_NOT_BILLABLE", "One of the selected charges does not belong to this patient or cannot be billed");
            }
            invoice.addItem(new InvoiceItem(invoice, c.type(), c.referenceId(), c.description(), c.quantity(), c.unitPrice()));
        }
        for (CreateInvoiceRequest.OtherItem o : nonNull(req.otherItems())) {
            invoice.addItem(new InvoiceItem(invoice, InvoiceItemType.OTHER, null, o.description().trim(), o.quantity(), o.unitPrice()));
        }
        if (invoice.getItems().isEmpty()) {
            throw new BusinessRuleException("EMPTY_INVOICE", "Select at least one charge or add an item");
        }
        BigDecimal discount = req.discountAmount() == null ? BigDecimal.ZERO : req.discountAmount();
        invoice.computeTotals(discount, settings.getTaxRatePercent());
        if (discount.compareTo(invoice.getSubtotal()) > 0) {
            throw new BusinessRuleException("DISCOUNT_TOO_HIGH", "The discount cannot be more than the subtotal");
        }
        if (discount.signum() > 0 && invoice.getNotes() == null) {
            throw new BusinessRuleException("DISCOUNT_REASON_REQUIRED", "Give the reason for the discount in the notes");
        }
        invoice.settleIfZero();
        invoiceRepository.saveAndFlush(invoice);
        notificationService.notify(patient.getUser().getId(), NotificationType.INVOICE_GENERATED, "New invoice " + number,
                "An invoice of " + money(invoice.getTotalAmount()) + " has been issued. You can view and download it in MediCare.",
                "/patient/invoices/" + invoice.getId(), "INVOICE:" + invoice.getId(), true);
        auditService.record("INVOICE_CREATED", "Invoice", invoice.getId(), AuditOutcome.SUCCESS,
                number + " total " + invoice.getTotalAmount());
        return response(invoice, actor);
    }

    @Transactional
    public InvoiceResponse cancel(AppUserPrincipal actor, Long id, String reason) {
        Invoice invoice = lock(id);
        if (invoice.getStatus() != InvoiceStatus.UNPAID || invoice.getAmountPaid().signum() != 0) {
            throw new BusinessRuleException("INVOICE_NOT_CANCELLABLE",
                    "Only unpaid invoices can be cancelled. Refund the payments first if needed.");
        }
        invoice.cancel(reason.trim());
        invoiceRepository.saveAndFlush(invoice);
        auditService.record("INVOICE_CANCELLED", "Invoice", id, AuditOutcome.SUCCESS, invoice.getInvoiceNo());
        return response(invoice, actor);
    }

    // ================================================================ money

    @Transactional
    public InvoiceResponse pay(AppUserPrincipal actor, Long id, PaymentRequest req) {
        Invoice invoice = lock(id);
        if (!PAYABLE.contains(invoice.getStatus())) {
            throw new BusinessRuleException("INVOICE_NOT_PAYABLE", "This invoice is " + label(invoice.getStatus()) + " and cannot take payments");
        }
        BigDecimal amount = req.amount();
        if (amount.compareTo(invoice.balanceDue()) > 0) {
            throw new BusinessRuleException("AMOUNT_EXCEEDS_BALANCE", "The balance due is " + money(invoice.balanceDue()));
        }
        String reference = checkReference(req.method(), req.transactionReference());
        Payment p = record(actor, invoice, PaymentType.PAYMENT, req.method(), amount, reference, blankToNull(req.notes()));
        invoice.applyMoney(amount, false);
        invoiceRepository.saveAndFlush(invoice);
        notificationService.notify(invoice.getPatient().getUser().getId(), NotificationType.PAYMENT_RECORDED, "Payment received",
                "We received " + money(amount) + " for invoice " + invoice.getInvoiceNo() + ". Balance due: "
                        + money(invoice.balanceDue()) + ".", "/patient/invoices/" + invoice.getId(), "PAYMENT:" + p.getId(), false);
        auditService.record("PAYMENT_RECORDED", "Invoice", id, AuditOutcome.SUCCESS, p.getPaymentNo() + " " + req.method() + " " + amount);
        return response(invoice, actor);
    }

    @Transactional
    public InvoiceResponse refund(AppUserPrincipal actor, Long id, PaymentRequest req) {
        Invoice invoice = lock(id);
        if (invoice.getAmountPaid().signum() == 0) {
            throw new BusinessRuleException("NOTHING_TO_REFUND", "Nothing has been paid on this invoice");
        }
        BigDecimal amount = req.amount();
        if (amount.compareTo(invoice.getAmountPaid()) > 0) {
            throw new BusinessRuleException("REFUND_EXCEEDS_PAID", "At most " + money(invoice.getAmountPaid()) + " can be refunded");
        }
        String reason = blankToNull(req.notes());
        if (reason == null || reason.length() < 3) {
            throw new BusinessRuleException("REASON_REQUIRED", "Enter the reason for the refund (at least 3 characters)");
        }
        String reference = checkReference(req.method(), req.transactionReference());
        Payment p = record(actor, invoice, PaymentType.REFUND, req.method(), amount, reference, reason);
        invoice.applyMoney(amount.negate(), true);
        invoiceRepository.saveAndFlush(invoice);
        notificationService.notify(invoice.getPatient().getUser().getId(), NotificationType.PAYMENT_RECORDED, "Refund issued",
                money(amount) + " has been refunded for invoice " + invoice.getInvoiceNo() + ".",
                "/patient/invoices/" + invoice.getId(), "REFUND:" + p.getId(), false);
        auditService.record("REFUND_RECORDED", "Invoice", id, AuditOutcome.SUCCESS, p.getPaymentNo() + " " + req.method() + " " + amount);
        return response(invoice, actor);
    }

    // ================================================================ read

    /** Receptionists and administrators: any invoice. Patients: their own (404 otherwise). */
    @Transactional(readOnly = true)
    public InvoiceResponse get(AppUserPrincipal actor, Long id) {
        Invoice invoice = readable(actor, id);
        return response(invoice, actor);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> search(String search, InvoiceStatus status, Long patientId, LocalDate from, LocalDate to,
                                               int page, int size) {
        return list(patientId, status, from, to, search, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> mine(AppUserPrincipal actor, InvoiceStatus status, int page, int size) {
        Patient patient = patientRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
        return list(patient.getId(), status, null, null, null, page, size);
    }

    @Transactional
    public Pdf pdf(AppUserPrincipal actor, Long id) {
        Invoice invoice = readable(actor, id);
        auditService.record("INVOICE_PDF_DOWNLOADED", "Invoice", id, AuditOutcome.SUCCESS, null);
        byte[] bytes = pdfGenerator.generate(invoice, paymentRepository.findByInvoiceIdOrderByPaidAtAscIdAsc(id),
                settingsService.getSettings(), clock.zone());
        return new Pdf(invoice.getInvoiceNo() + ".pdf", bytes);
    }

    public record Pdf(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public BillingSummary summary(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (end.isBefore(start)) {
            throw new BusinessRuleException("INVALID_DATES", "'To' must be on or after 'from'");
        }
        ZoneId zone = clock.zone();
        Instant a = start.atStartOfDay(zone).toInstant();
        Instant b = end.plusDays(1).atStartOfDay(zone).toInstant();

        Map<InvoiceStatus, Long> byStatus = new EnumMap<>(InvoiceStatus.class);
        long count = 0;
        BigDecimal invoiced = BigDecimal.ZERO;
        for (Object[] r : invoiceRepository.statusTotals(a, b)) {
            InvoiceStatus s = (InvoiceStatus) r[0];
            long n = ((Number) r[1]).longValue();
            byStatus.put(s, n);
            count += n;
            if (s != InvoiceStatus.CANCELLED) {
                invoiced = invoiced.add((BigDecimal) r[2]);
            }
        }
        Map<PaymentMethod, BigDecimal> byMethod = new EnumMap<>(PaymentMethod.class);
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        for (Object[] r : paymentRepository.totalsByMethod(a, b)) {
            BigDecimal amt = (BigDecimal) r[2];
            if (r[0] == PaymentType.PAYMENT) {
                collected = collected.add(amt);
                byMethod.merge((PaymentMethod) r[1], amt, BigDecimal::add);
            } else {
                refunded = refunded.add(amt);
            }
        }
        Map<InvoiceItemType, BigDecimal> byType = new EnumMap<>(InvoiceItemType.class);
        for (Object[] r : invoiceRepository.itemTypeTotals(a, b)) {
            byType.put((InvoiceItemType) r[0], (BigDecimal) r[1]);
        }
        Object[] open = invoiceRepository.outstanding().getFirst();
        return new BillingSummary(start, end, count, invoiced, collected, refunded, collected.subtract(refunded),
                ((Number) open[0]).longValue(), (BigDecimal) open[1], byStatus, byMethod, byType);
    }

    // ================================================================ helpers

    /** Everything the patient received and has not been billed for, keyed like {@link InvoiceItem#key}. */
    private Map<String, BillableCharge> unbilled(Long patientId) {
        Set<String> billed = new HashSet<>(itemRepository.billedReferences(patientId));
        Map<String, BillableCharge> out = new LinkedHashMap<>();
        ZoneId zone = clock.zone();
        for (Appointment a : sourceRepository.appointments(patientId, BILLABLE_VISITS)) {
            BigDecimal fee = a.getConsultationFee() != null ? a.getConsultationFee() : a.getDoctor().getConsultationFee();
            add(out, billed, new BillableCharge(InvoiceItemType.CONSULTATION, a.getId(),
                    "Consultation - " + a.getDoctor().getUser().getFullName() + " (" + a.getDoctor().getDepartment().getName()
                            + "), " + LABEL_DATE.format(a.getAppointmentDate()), 1, fee, fee, a.getAppointmentDate(), a.getReferenceNo()));
        }
        for (LabOrderItem i : sourceRepository.labItems(patientId)) {
            var o = i.getOrder();
            add(out, billed, new BillableCharge(InvoiceItemType.LAB_TEST, i.getId(), "Lab test - " + i.getTestName(), 1,
                    i.getPriceAtOrder(), i.getPriceAtOrder(), o.getOrderedAt().atZone(zone).toLocalDate(), o.getOrderNo()));
        }
        for (DispensingItem i : sourceRepository.dispensingItems(patientId)) {
            var m = i.getMedicine();
            BigDecimal price = i.getUnitPrice() != null ? i.getUnitPrice() : m.getUnitPrice();
            add(out, billed, new BillableCharge(InvoiceItemType.PHARMACY, i.getId(),
                    "Medicine - " + m.getName() + " " + m.getStrength(), i.getQuantity(), price,
                    price.multiply(BigDecimal.valueOf(i.getQuantity())), i.getRecord().getDispensedAt().atZone(zone).toLocalDate(),
                    i.getRecord().getDispensingNo()));
        }
        return out;
    }

    private static void add(Map<String, BillableCharge> out, Set<String> billed, BillableCharge c) {
        String key = InvoiceItem.key(c.type(), c.referenceId());
        if (!billed.contains(key)) {
            out.put(key, c);
        }
    }

    private Payment record(AppUserPrincipal actor, Invoice invoice, PaymentType type, PaymentMethod method, BigDecimal amount,
                           String reference, String notes) {
        String number = (type == PaymentType.REFUND ? "RFD-" : "PAY-") + NO_DATE.format(clock.today()) + "-"
                + String.format("%06d", sequenceService.next(SequenceService.PAYMENT));
        return paymentRepository.save(new Payment(number, invoice, type, method, amount, reference, notes,
                userRepository.getReferenceById(actor.id())));
    }

    /** Non-cash payments need a reference. Anything resembling a card number is refused and never stored. */
    static String checkReference(PaymentMethod method, String reference) {
        String ref = blankToNull(reference);
        if (ref != null && CARD_LIKE.matcher(ref.replaceAll("[\\s-]", "")).find()) {
            throw new BusinessRuleException("CARD_NUMBER_NOT_ALLOWED",
                    "Do not enter card numbers. Use the terminal slip or transaction reference instead.");
        }
        if (method != PaymentMethod.CASH && (ref == null || ref.length() < 3)) {
            throw new BusinessRuleException("REFERENCE_REQUIRED", "Enter the transaction reference for non-cash payments");
        }
        return ref;
    }

    private Invoice readable(AppUserPrincipal actor, Long id) {
        Invoice invoice = invoiceRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
        if (actor.role() == Role.PATIENT && !invoice.getPatient().getUser().getId().equals(actor.id())) {
            throw new ResourceNotFoundException("Invoice", id);
        }
        return invoice;
    }

    private Invoice lock(Long id) {
        invoiceRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
        return invoiceRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    private PageResponse<InvoiceSummary> list(Long patientId, InvoiceStatus status, LocalDate from, LocalDate to, String search,
                                              int page, int size) {
        ZoneId zone = clock.zone();
        Instant a = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant b = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        Set<InvoiceStatus> statuses = status == null ? EnumSet.allOf(InvoiceStatus.class) : EnumSet.of(status);
        String term = blankToNull(search);
        var result = invoiceRepository.search(patientId, statuses, a, b, term, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "issuedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PageResponse.from(result, i -> new InvoiceSummary(i.getId(), i.getInvoiceNo(), i.getStatus(), i.getPatient().getId(),
                i.getPatient().getUser().getFullName(), i.getPatient().getPatientCode(), i.getTotalAmount(), i.getAmountPaid(),
                i.balanceDue(), i.getIssuedAt()));
    }

    private InvoiceResponse response(Invoice i, AppUserPrincipal actor) {
        boolean desk = actor.role() == Role.RECEPTIONIST;
        List<InvoiceResponse.PaymentLine> payments = new ArrayList<>();
        for (Payment p : paymentRepository.findByInvoiceIdOrderByPaidAtAscIdAsc(i.getId())) {
            payments.add(new InvoiceResponse.PaymentLine(p.getId(), p.getPaymentNo(), p.getType(), p.getMethod(), p.getAmount(),
                    p.getTransactionReference(), p.getNotes(), p.getRecordedBy().getFullName(), p.getPaidAt()));
        }
        return new InvoiceResponse(i.getId(), i.getInvoiceNo(), i.getStatus(), i.getPatient().getId(),
                i.getPatient().getUser().getFullName(), i.getPatient().getPatientCode(), i.getSubtotal(), i.getDiscountAmount(),
                i.getTaxRatePercent(), i.getTaxAmount(), i.getTotalAmount(), i.getAmountPaid(), i.balanceDue(), i.getNotes(),
                i.getIssuedBy().getFullName(), i.getIssuedAt(), i.getCancelledAt(), i.getCancellationReason(),
                i.getItems().stream().map(it -> new InvoiceResponse.Item(it.getId(), it.getType(), it.getDescription(),
                        it.getQuantity(), it.getUnitPrice(), it.getLineTotal())).toList(),
                payments,
                desk && PAYABLE.contains(i.getStatus()) && i.balanceDue().signum() > 0,
                desk && i.getAmountPaid().signum() > 0,
                desk && i.getStatus() == InvoiceStatus.UNPAID && i.getAmountPaid().signum() == 0);
    }

    static String money(BigDecimal v) {
        return "Rs. " + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String label(InvoiceStatus s) {
        return s.name().toLowerCase().replace('_', ' ');
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
