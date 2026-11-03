package com.medicare.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.medicare.entity.Appointment;
import com.medicare.enums.AppointmentStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "doctor.department"})
    Optional<Appointment> findWithDetailsById(Long id);

    /** Appointments that occupy time on a doctor's day (everything except CANCELLED). */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date
              AND a.status <> com.medicare.enums.AppointmentStatus.CANCELLED
            """)
    List<Appointment> findOccupying(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    /** True if an occupying appointment of the doctor overlaps [start, end), optionally ignoring one id. */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date
              AND a.status <> com.medicare.enums.AppointmentStatus.CANCELLED
              AND a.startTime < :end AND a.endTime > :start
              AND (:excludeId IS NULL OR a.id <> :excludeId)
            """)
    boolean doctorHasOverlap(@Param("doctorId") Long doctorId, @Param("date") LocalDate date,
                             @Param("start") LocalTime start, @Param("end") LocalTime end,
                             @Param("excludeId") Long excludeId);

    /** True if the patient already has an open appointment (any doctor) overlapping [start, end). */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.patient.id = :patientId AND a.appointmentDate = :date
              AND a.status IN (com.medicare.enums.AppointmentStatus.SCHEDULED,
                               com.medicare.enums.AppointmentStatus.CHECKED_IN,
                               com.medicare.enums.AppointmentStatus.IN_PROGRESS)
              AND a.startTime < :end AND a.endTime > :start
              AND (:excludeId IS NULL OR a.id <> :excludeId)
            """)
    boolean patientHasOverlap(@Param("patientId") Long patientId, @Param("date") LocalDate date,
                              @Param("start") LocalTime start, @Param("end") LocalTime end,
                              @Param("excludeId") Long excludeId);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department", "patient", "patient.user"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.patient.id = :patientId
              AND a.status IN :statuses
              AND (:fromDate IS NULL OR a.appointmentDate >= :fromDate)
              AND (:toDate IS NULL OR a.appointmentDate <= :toDate)
            """)
    Page<Appointment> findForPatient(@Param("patientId") Long patientId,
                                     @Param("statuses") Collection<AppointmentStatus> statuses,
                                     @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                     Pageable pageable);

    /** Past = finished (completed / no-show) or dated before today, excluding cancelled ones. */
    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department", "patient", "patient.user"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.patient.id = :patientId
              AND a.status <> com.medicare.enums.AppointmentStatus.CANCELLED
              AND (a.status IN (com.medicare.enums.AppointmentStatus.COMPLETED, com.medicare.enums.AppointmentStatus.NO_SHOW)
                   OR a.appointmentDate < :today)
            """)
    Page<Appointment> findPastForPatient(@Param("patientId") Long patientId, @Param("today") LocalDate today,
                                         Pageable pageable);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department", "patient", "patient.user"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.status IN :statuses
              AND (:fromDate IS NULL OR a.appointmentDate >= :fromDate)
              AND (:toDate IS NULL OR a.appointmentDate <= :toDate)
            """)
    Page<Appointment> findForDoctor(@Param("doctorId") Long doctorId,
                                    @Param("statuses") Collection<AppointmentStatus> statuses,
                                    @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                    Pageable pageable);

    /** Future scheduled appointments of a doctor (for re-checking after schedule/leave changes). */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctor.id = :doctorId AND a.status = com.medicare.enums.AppointmentStatus.SCHEDULED
              AND a.appointmentDate >= :fromDate
            ORDER BY a.appointmentDate, a.startTime
            """)
    List<Appointment> findScheduledFrom(@Param("doctorId") Long doctorId, @Param("fromDate") LocalDate fromDate);

    @Query("SELECT a.status, COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId GROUP BY a.status")
    List<Object[]> countByStatusForPatient(@Param("patientId") Long patientId);

    @Query("""
            SELECT a.status, COUNT(a) FROM Appointment a
            WHERE a.doctor.id = :doctorId AND a.appointmentDate BETWEEN :fromDate AND :toDate
            GROUP BY a.status
            """)
    List<Object[]> countByStatusForDoctor(@Param("doctorId") Long doctorId, @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    boolean existsByDoctorIdAndPatientIdAndStatusNot(Long doctorId, Long patientId, AppointmentStatus status);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    List<Appointment> findByPatientIdOrderByAppointmentDateDescStartTimeDesc(Long patientId);
}
