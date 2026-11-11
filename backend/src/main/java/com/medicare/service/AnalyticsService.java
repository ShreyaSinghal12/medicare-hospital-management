package com.medicare.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.medicare.dto.admin.AdminDashboard;
import com.medicare.dto.inventory.InventoryAlerts;
import com.medicare.repository.InvoiceRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statistics for the administrator dashboard. Only counts and amounts are computed; no diagnoses, results or
 * other clinical content is read. Days are hospital-local dates.
 */
@Service
public class AnalyticsService {

    private final JdbcTemplate jdbc;
    private final HospitalClock clock;
    private final InventoryService inventoryService;
    private final InvoiceRepository invoiceRepository;

    public AnalyticsService(JdbcTemplate jdbc, HospitalClock clock, InventoryService inventoryService, InvoiceRepository invoiceRepository) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.inventoryService = inventoryService;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboard dashboard(LocalDate from, LocalDate to) {
        ZoneId zone = clock.zone();
        DateRange r = DateRange.of(from, to, clock.today(), 30, zone);
        Timestamp start = Timestamp.from(r.start());
        Timestamp end = Timestamp.from(r.end());

        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM appointments WHERE appointment_date BETWEEN ? AND ? GROUP BY status ORDER BY status",
                rs -> { byStatus.put(rs.getString(1), rs.getLong(2)); }, r.from(), r.to());
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        Map<LocalDate, Long> perDay = new TreeMap<>();
        for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) {
            perDay.put(d, 0L);
        }
        jdbc.query("""
                SELECT appointment_date, COUNT(*) FROM appointments
                WHERE appointment_date BETWEEN ? AND ? AND status <> 'CANCELLED' GROUP BY appointment_date
                """, rs -> { perDay.put(rs.getDate(1).toLocalDate(), rs.getLong(2)); }, r.from(), r.to());

        List<AdminDashboard.NamedCount> byDept = jdbc.query("""
                SELECT dep.name, COUNT(*) FROM appointments a JOIN doctors d ON d.id = a.doctor_id JOIN departments dep ON dep.id = d.department_id
                WHERE a.appointment_date BETWEEN ? AND ? AND a.status <> 'CANCELLED' GROUP BY dep.name ORDER BY COUNT(*) DESC
                """, (rs, i) -> new AdminDashboard.NamedCount(rs.getString(1), rs.getLong(2)), r.from(), r.to());

        List<AdminDashboard.DoctorLoad> workload = jdbc.query("""
                SELECT d.id, u.full_name, dep.name, COUNT(*),
                       SUM(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN a.status = 'NO_SHOW' THEN 1 ELSE 0 END)
                FROM appointments a JOIN doctors d ON d.id = a.doctor_id JOIN users u ON u.id = d.user_id
                JOIN departments dep ON dep.id = d.department_id
                WHERE a.appointment_date BETWEEN ? AND ? AND a.status <> 'CANCELLED'
                GROUP BY d.id, u.full_name, dep.name ORDER BY COUNT(*) DESC LIMIT 10
                """, (rs, i) -> new AdminDashboard.DoctorLoad(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                rs.getLong(5), rs.getLong(6)), r.from(), r.to());

        Map<LocalDate, BigDecimal> revenue = new TreeMap<>();
        perDay.keySet().forEach(d -> revenue.put(d, BigDecimal.ZERO));
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("SELECT paid_at, payment_type, amount FROM payments WHERE paid_at >= ? AND paid_at < ?", rs -> {
            LocalDate day = rs.getTimestamp(1).toInstant().atZone(zone).toLocalDate();
            BigDecimal amount = rs.getBigDecimal(3);
            boolean refund = "REFUND".equals(rs.getString(2));
            revenue.merge(day, refund ? amount.negate() : amount, BigDecimal::add);
            totals[refund ? 1 : 0] = totals[refund ? 1 : 0].add(amount);
        }, start, end);

        Map<String, Long> labs = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM lab_orders WHERE ordered_at >= ? AND ordered_at < ? GROUP BY status ORDER BY status",
                rs -> { labs.put(rs.getString(1), rs.getLong(2)); }, start, end);

        long newPatients = count("SELECT COUNT(*) FROM patients WHERE created_at >= ? AND created_at < ?", start, end);
        long activePatients = count("SELECT COUNT(*) FROM patients p JOIN users u ON u.id = p.user_id WHERE u.active = TRUE");
        long activeDoctors = count("SELECT COUNT(*) FROM doctors d JOIN users u ON u.id = d.user_id WHERE u.active = TRUE");
        Object[] open = invoiceRepository.outstanding().getFirst();
        InventoryAlerts alerts = inventoryService.alerts();

        List<AdminDashboard.DayCount> days = new ArrayList<>();
        perDay.forEach((d, c) -> days.add(new AdminDashboard.DayCount(d, c)));
        List<AdminDashboard.DayAmount> money = new ArrayList<>();
        revenue.forEach((d, a) -> money.add(new AdminDashboard.DayAmount(d, a)));
        return new AdminDashboard(r.from(), r.to(), total, byStatus.getOrDefault("COMPLETED", 0L),
                byStatus.getOrDefault("CANCELLED", 0L), byStatus.getOrDefault("NO_SHOW", 0L), newPatients, activePatients,
                activeDoctors, totals[0], totals[1], (BigDecimal) open[1], ((Number) open[0]).longValue(),
                alerts.lowStock().size(), alerts.expiringSoon().size(), alerts.expired().size(),
                byStatus, days, byDept, workload, money, labs);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
