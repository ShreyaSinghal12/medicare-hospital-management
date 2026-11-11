package com.medicare.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.medicare.dto.inventory.MedicineSummary;
import com.medicare.enums.AuditOutcome;
import com.medicare.exception.BusinessRuleException;
import com.medicare.reporting.CsvWriter;
import com.medicare.reporting.ReportPdfGenerator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative reports as on-screen preview, CSV or PDF. Reports contain operational data only: no diagnoses,
 * visit reasons, prescriptions, test names or results. At most {@link #MAX_ROWS} rows; every export is audited.
 */
@Service
public class ReportService {

    public static final int MAX_ROWS = 10_000;
    public static final int PREVIEW_ROWS = 100;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    public enum Type {
        APPOINTMENTS("Appointments"), PAYMENTS("Payments and refunds"), INVOICES("Invoices"),
        LAB_WORKLOAD("Laboratory workload"), INVENTORY("Medicine stock"), AUDIT("Audit log");

        public final String title;

        Type(String title) {
            this.title = title;
        }
    }

    public enum Format { JSON, CSV, PDF }

    public record Report(Type type, String title, LocalDate from, LocalDate to, List<String> columns, List<List<String>> rows,
                         long totalRows, boolean truncated) {
    }

    public record FileResult(String fileName, String contentType, byte[] content) {
    }

    private final JdbcTemplate jdbc;
    private final HospitalClock clock;
    private final InventoryService inventoryService;
    private final HospitalSettingsService settingsService;
    private final ReportPdfGenerator pdfGenerator;
    private final AuditService auditService;

    public ReportService(JdbcTemplate jdbc, HospitalClock clock, InventoryService inventoryService, HospitalSettingsService settingsService,
                         ReportPdfGenerator pdfGenerator, AuditService auditService) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.inventoryService = inventoryService;
        this.settingsService = settingsService;
        this.pdfGenerator = pdfGenerator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Report preview(Type type, LocalDate from, LocalDate to) {
        Report full = build(type, from, to);
        List<List<String>> rows = full.rows().size() > PREVIEW_ROWS ? full.rows().subList(0, PREVIEW_ROWS) : full.rows();
        return new Report(type, full.title(), full.from(), full.to(), full.columns(), List.copyOf(rows), full.totalRows(),
                full.truncated() || full.rows().size() > PREVIEW_ROWS);
    }

    @Transactional
    public FileResult export(Type type, Format format, LocalDate from, LocalDate to) {
        if (format == Format.JSON) {
            throw new BusinessRuleException("INVALID_FORMAT", "Choose CSV or PDF");
        }
        Report r = build(type, from, to);
        String name = "medicare-" + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-" + r.from() + "_" + r.to();
        auditService.record("REPORT_EXPORTED", "Report", type.name(), AuditOutcome.SUCCESS,
                format + " " + r.from() + ".." + r.to() + ", " + r.rows().size() + " row(s)");
        if (format == Format.CSV) {
            return new FileResult(name + ".csv", "text/csv; charset=UTF-8", CsvWriter.write(r.columns(), r.rows()));
        }
        String subtitle = r.from() + " to " + r.to() + " (" + clock.zone().getId() + ")  ·  " + r.rows().size() + " row(s)"
                + (r.truncated() ? " (first " + MAX_ROWS + " only)" : "") + "  ·  generated " + DATE_TIME.format(Instant.now().atZone(clock.zone()));
        return new FileResult(name + ".pdf", "application/pdf",
                pdfGenerator.generate(settingsService.getSettings().getHospitalName(), r.title(), subtitle, r.columns(), r.rows()));
    }

    private Report build(Type type, LocalDate from, LocalDate to) {
        ZoneId zone = clock.zone();
        DateRange range = DateRange.of(from, to, clock.today(), 30, zone);
        Timestamp a = Timestamp.from(range.start());
        Timestamp b = Timestamp.from(range.end());
        List<String> cols;
        List<List<String>> rows;
        switch (type) {
            case APPOINTMENTS -> {
                cols = List.of("Date", "Time", "Reference", "Patient ID", "Patient", "Doctor", "Department", "Type", "Status");
                rows = jdbc.query("""
                        SELECT a.appointment_date, a.start_time, a.reference_no, p.patient_code, pu.full_name, du.full_name, dep.name,
                               a.appointment_type, a.status
                        FROM appointments a JOIN patients p ON p.id = a.patient_id JOIN users pu ON pu.id = p.user_id
                        JOIN doctors d ON d.id = a.doctor_id JOIN users du ON du.id = d.user_id JOIN departments dep ON dep.id = d.department_id
                        WHERE a.appointment_date BETWEEN ? AND ? ORDER BY a.appointment_date, a.start_time, a.id LIMIT ?
                        """, (rs, i) -> List.of(rs.getDate(1).toString(), rs.getTime(2).toLocalTime().toString(), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), label(rs.getString(8)), label(rs.getString(9))),
                        range.from(), range.to(), MAX_ROWS + 1);
            }
            case PAYMENTS -> {
                cols = List.of("Date and time", "Number", "Type", "Method", "Amount (INR)", "Invoice", "Patient ID", "Recorded by");
                rows = jdbc.query("""
                        SELECT pm.paid_at, pm.payment_no, pm.payment_type, pm.method, pm.amount, i.invoice_no, p.patient_code, u.full_name
                        FROM payments pm JOIN invoices i ON i.id = pm.invoice_id JOIN patients p ON p.id = i.patient_id
                        JOIN users u ON u.id = pm.recorded_by_user_id
                        WHERE pm.paid_at >= ? AND pm.paid_at < ? ORDER BY pm.paid_at, pm.id LIMIT ?
                        """, (rs, i) -> List.of(time(rs, 1, zone), rs.getString(2), label(rs.getString(3)),
                        label(rs.getString(4).replace("_SIMULATED", "")),
                        ("REFUND".equals(rs.getString(3)) ? "-" : "") + money(rs.getBigDecimal(5)), rs.getString(6), rs.getString(7),
                        rs.getString(8)), a, b, MAX_ROWS + 1);
            }
            case INVOICES -> {
                cols = List.of("Issued", "Invoice", "Patient ID", "Patient", "Status", "Total (INR)", "Paid", "Refunded", "Balance due");
                rows = jdbc.query("""
                        SELECT i.issued_at, i.invoice_no, p.patient_code, u.full_name, i.status, i.total_amount, i.amount_paid,
                               i.refunded_amount, CASE WHEN i.status = 'CANCELLED' THEN 0 ELSE i.total_amount - i.amount_paid END
                        FROM invoices i JOIN patients p ON p.id = i.patient_id JOIN users u ON u.id = p.user_id
                        WHERE i.issued_at >= ? AND i.issued_at < ? ORDER BY i.issued_at, i.id LIMIT ?
                        """, (rs, i) -> List.of(time(rs, 1, zone), rs.getString(2), rs.getString(3), rs.getString(4), label(rs.getString(5)),
                        money(rs.getBigDecimal(6)), money(rs.getBigDecimal(7)), money(rs.getBigDecimal(8)), money(rs.getBigDecimal(9))),
                        a, b, MAX_ROWS + 1);
            }
            case LAB_WORKLOAD -> {
                cols = List.of("Ordered", "Order", "Doctor", "Patient ID", "Tests", "Priority", "Status", "Completed");
                rows = jdbc.query("""
                        SELECT o.ordered_at, o.order_no, du.full_name, p.patient_code,
                               (SELECT COUNT(*) FROM lab_order_items it WHERE it.lab_order_id = o.id), o.priority, o.status, o.completed_at
                        FROM lab_orders o JOIN doctors d ON d.id = o.doctor_id JOIN users du ON du.id = d.user_id JOIN patients p ON p.id = o.patient_id
                        WHERE o.ordered_at >= ? AND o.ordered_at < ? ORDER BY o.ordered_at, o.id LIMIT ?
                        """, (rs, i) -> List.of(time(rs, 1, zone), rs.getString(2), rs.getString(3), rs.getString(4),
                        String.valueOf(rs.getInt(5)), label(rs.getString(6)), label(rs.getString(7)), time(rs, 8, zone)), a, b, MAX_ROWS + 1);
            }
            case INVENTORY -> {
                cols = List.of("Medicine", "Strength", "Form", "Manufacturer", "Usable stock", "Expired stock", "Reorder level",
                        "Low stock", "Next expiry", "Unit price (INR)", "Active");
                rows = new ArrayList<>();
                List<MedicineSummary> all = new ArrayList<>();
                for (int page = 0; all.size() <= MAX_ROWS; page++) {
                    var batch = inventoryService.search(null, null, true, page, 100);
                    all.addAll(batch.content());
                    if (page + 1 >= batch.totalPages()) {
                        break;
                    }
                }
                for (MedicineSummary m : all) {
                    rows.add(List.of(m.name(), m.strength(), label(m.category().name()), m.manufacturer(),
                            String.valueOf(m.availableQuantity()), String.valueOf(m.expiredQuantity()), String.valueOf(m.reorderLevel()),
                            m.lowStock() ? "Yes" : "No", m.nearestExpiry() == null ? "" : m.nearestExpiry().toString(),
                            money(m.unitPrice()), m.active() ? "Yes" : "No"));
                }
            }
            case AUDIT -> {
                cols = List.of("Time", "User", "Role", "Action", "Record", "Record ID", "Outcome", "IP address", "Details");
                rows = jdbc.query("""
                        SELECT created_at, actor_email, actor_role, action, entity_type, entity_id, outcome, ip_address, details
                        FROM audit_logs WHERE created_at >= ? AND created_at < ? ORDER BY created_at, id LIMIT ?
                        """, (rs, i) -> List.of(time(rs, 1, zone), nz(rs.getString(2)), nz(rs.getString(3)), rs.getString(4),
                        nz(rs.getString(5)), nz(rs.getString(6)), rs.getString(7), nz(rs.getString(8)), nz(rs.getString(9))), a, b, MAX_ROWS + 1);
            }
            default -> throw new IllegalArgumentException(type.name());
        }
        boolean truncated = rows.size() > MAX_ROWS;
        List<List<String>> kept = truncated ? rows.subList(0, MAX_ROWS) : rows;
        return new Report(type, type.title, range.from(), range.to(), cols, kept, kept.size(), truncated);
    }

    private static String time(ResultSet rs, int col, ZoneId zone) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? "" : DATE_TIME.format(t.toInstant().atZone(zone));
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String label(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        String s = v.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
