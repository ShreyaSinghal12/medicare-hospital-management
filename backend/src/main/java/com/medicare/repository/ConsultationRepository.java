package com.medicare.repository;

import java.util.List;
import java.util.Optional;

import com.medicare.entity.Consultation;
import com.medicare.enums.ConsultationStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    @EntityGraph(attributePaths = {"appointment", "patient", "patient.user", "doctor", "doctor.user", "doctor.department"})
    Optional<Consultation> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"appointment", "patient", "patient.user", "doctor", "doctor.user", "doctor.department"})
    Optional<Consultation> findByAppointmentId(Long appointmentId);

    /** A patient's consultations, newest first, with doctor and appointment details. */
    @EntityGraph(attributePaths = {"appointment", "doctor", "doctor.user", "doctor.department"})
    List<Consultation> findByPatientIdOrderByAppointmentAppointmentDateDescAppointmentStartTimeDesc(Long patientId);

    @EntityGraph(attributePaths = {"appointment", "doctor", "doctor.user", "doctor.department"})
    List<Consultation> findByPatientIdAndStatusOrderByAppointmentAppointmentDateDescAppointmentStartTimeDesc(
            Long patientId, ConsultationStatus status);
}
