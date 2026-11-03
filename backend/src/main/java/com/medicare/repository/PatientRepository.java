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
}
