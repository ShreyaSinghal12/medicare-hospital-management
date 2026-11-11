package com.medicare.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.medicare.entity.Prescription;
import com.medicare.enums.PrescriptionStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "doctor.department",
            "consultation", "consultation.appointment"})
    Optional<Prescription> findWithDetailsById(Long id);

    /** All versions issued from a consultation, newest first. */
    @EntityGraph(attributePaths = {"doctor", "doctor.user"})
    List<Prescription> findByConsultationIdOrderByVersionNumberDesc(Long consultationId);

    /** All versions with the same number (the revision chain), newest first. */
    List<Prescription> findByPrescriptionNoOrderByVersionNumberDesc(String prescriptionNo);

    boolean existsByPrescriptionNoAndStatus(String prescriptionNo, PrescriptionStatus status);

    /** The current (finalized) version with this number, with its items and people, for dispensing. */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "items"})
    Optional<Prescription> findFirstByPrescriptionNoIgnoreCaseAndStatus(String prescriptionNo, PrescriptionStatus status);

    /** Locks the prescription row so two dispensings of the same prescription run one after another. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prescription p WHERE p.id = :id")
    Optional<Prescription> lockById(@Param("id") Long id);

    /**
     * Search over prescriptions of a patient or a doctor (one of the ids is given). Text matches the
     * prescription number, the doctor/patient name or any medicine name.
     */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "doctor.department", "consultation",
            "consultation.appointment"})
    @Query("""
            SELECT p FROM Prescription p
            WHERE (:patientId IS NULL OR p.patient.id = :patientId)
              AND (:doctorId IS NULL OR p.doctor.id = :doctorId)
              AND p.status IN :statuses
              AND (:fromTime IS NULL OR p.createdAt >= :fromTime)
              AND (:toTime IS NULL OR p.createdAt < :toTime)
              AND (:search IS NULL
                   OR LOWER(p.prescriptionNo) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.doctor.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.patient.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (SELECT 1 FROM PrescriptionItem i WHERE i.prescription = p
                              AND LOWER(i.medicineName) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<Prescription> search(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId,
                              @Param("statuses") Collection<PrescriptionStatus> statuses,
                              @Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime,
                              @Param("search") String search, Pageable pageable);

    /** Current (finalized) prescriptions of the given consultations, for the medical-history timeline. */
    @Query("SELECT p FROM Prescription p WHERE p.consultation.id IN :consultationIds AND p.status = com.medicare.enums.PrescriptionStatus.FINALIZED")
    List<Prescription> findCurrentForConsultations(@Param("consultationIds") Collection<Long> consultationIds);
}
