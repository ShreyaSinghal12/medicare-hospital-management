package com.medicare.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Operational statistics for administrators. Counts and amounts only, no clinical content. */
public record AdminDashboard(
        LocalDate from,
        LocalDate to,
        long totalAppointments,
        long completedAppointments,
        long cancelledAppointments,
        long noShowAppointments,
        long newPatients,
        long activePatients,
        long activeDoctors,
        BigDecimal collected,
        BigDecimal refunded,
        BigDecimal outstanding,
        long outstandingInvoices,
        long lowStockMedicines,
        long expiringBatches,
        long expiredBatches,
        Map<String, Long> appointmentsByStatus,
        List<DayCount> appointmentsPerDay,
        List<NamedCount> appointmentsByDepartment,
        List<DoctorLoad> doctorWorkload,
        List<DayAmount> revenuePerDay,
        Map<String, Long> labOrdersByStatus) {

    public record DayCount(LocalDate date, long count) {
    }

    public record DayAmount(LocalDate date, BigDecimal amount) {
    }

    public record NamedCount(String name, long count) {
    }

    public record DoctorLoad(Long doctorId, String doctorName, String department, long total, long completed, long noShow) {
    }
}
