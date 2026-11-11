package com.medicare.repository;

import java.time.LocalDate;
import java.util.List;

import com.medicare.entity.Appointment;
import com.medicare.entity.Consultation;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries for the reminder scheduler. */
public interface ReminderQueryRepository extends Repository<Appointment, Long> {

    @Query("""
            SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH p.user JOIN FETCH a.doctor d JOIN FETCH d.user
            JOIN FETCH d.department
            WHERE a.status = com.medicare.enums.AppointmentStatus.SCHEDULED AND a.appointmentDate BETWEEN :from AND :to
            """)
    List<Appointment> scheduledBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT c FROM Consultation c JOIN FETCH c.patient p JOIN FETCH p.user JOIN FETCH c.doctor d JOIN FETCH d.user
            JOIN FETCH c.appointment
            WHERE c.status = com.medicare.enums.ConsultationStatus.FINALIZED AND c.followUpDate BETWEEN :from AND :to
            """)
    List<Consultation> followUpsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Whether the patient already has an open booking with this doctor after the given appointment. */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.patient.id = :patientId AND a.doctor.id = :doctorId AND a.id <> :excludeId
              AND a.appointmentDate >= :fromDate
              AND a.status IN (com.medicare.enums.AppointmentStatus.SCHEDULED, com.medicare.enums.AppointmentStatus.CHECKED_IN,
                               com.medicare.enums.AppointmentStatus.IN_PROGRESS, com.medicare.enums.AppointmentStatus.COMPLETED)
            """)
    boolean hasLaterVisit(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId,
                          @Param("excludeId") Long excludeId, @Param("fromDate") LocalDate fromDate);
}
