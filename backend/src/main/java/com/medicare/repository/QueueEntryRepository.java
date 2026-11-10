package com.medicare.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.medicare.entity.QueueEntry;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    Optional<QueueEntry> findByAppointmentId(Long appointmentId);

    List<QueueEntry> findByAppointmentIdIn(Collection<Long> appointmentIds);

    /**
     * Highest token issued so far. A locking read (FOR UPDATE) is used on purpose: under MySQL's default
     * REPEATABLE READ isolation a plain SELECT would read the transaction's old snapshot and could miss a
     * token committed by a concurrent check-in, even after the doctor-row lock was acquired.
     */
    @Query(value = "SELECT COALESCE(MAX(token_number), 0) FROM queue_entries WHERE doctor_id = :doctorId AND queue_date = :date FOR UPDATE",
            nativeQuery = true)
    int maxToken(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.patient.user",
            "appointment.doctor", "appointment.doctor.user"})
    @Query("""
            SELECT q FROM QueueEntry q
            WHERE q.queueDate = :date AND (:doctorId IS NULL OR q.doctorId = :doctorId)
            ORDER BY q.doctorId, q.tokenNumber
            """)
    List<QueueEntry> findBoard(@Param("date") LocalDate date, @Param("doctorId") Long doctorId);
}
