package com.medicare.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.medicare.entity.Patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<Patient> findByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<Patient> findWithUserById(Long id);

    Optional<Patient> findByPatientCode(String patientCode);

    long countByCreatedAtBetween(java.time.Instant from, java.time.Instant to);

    @EntityGraph(attributePaths = "user")
    List<Patient> findWithUserByIdIn(Collection<Long> ids);

    /** Search by name, e-mail, phone or patient code. Returns demographic data only. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT p FROM Patient p JOIN p.user u
            WHERE (:active IS NULL OR u.active = :active)
              AND (:search IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR u.phone LIKE CONCAT('%', :search, '%')
                   OR LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Patient> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    /** Patients the doctor has (or had) a treatment relationship with: any non-cancelled appointment. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT p FROM Patient p JOIN p.user u
            WHERE EXISTS (SELECT 1 FROM Appointment a WHERE a.patient = p AND a.doctor.id = :doctorId
                          AND a.status <> com.medicare.enums.AppointmentStatus.CANCELLED)
              AND (:search IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Patient> findPatientsOfDoctor(@Param("doctorId") Long doctorId, @Param("search") String search,
                                       Pageable pageable);

    /** Lightweight rows used for duplicate detection. */
    interface DuplicateCandidate {
        Long getId();
        String getFullName();
        LocalDate getDateOfBirth();
        String getPhone();
    }

    @Query("SELECT p.id AS id, u.fullName AS fullName, p.dateOfBirth AS dateOfBirth, u.phone AS phone "
            + "FROM Patient p JOIN p.user u")
    List<DuplicateCandidate> findDuplicateCandidates();

    /** Locks the patient row so that two invoices for the same patient are created one after another. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Patient p WHERE p.id = :id")
    java.util.Optional<Patient> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
