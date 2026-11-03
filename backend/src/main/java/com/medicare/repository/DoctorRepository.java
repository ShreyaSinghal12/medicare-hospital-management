package com.medicare.repository;

import java.util.List;
import java.util.Optional;

import com.medicare.entity.Doctor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Doctor> findByUserId(Long userId);

    /** Loads a doctor together with the user and department (avoids lazy-loading round trips). */
    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Doctor> findWithDetailsById(Long id);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    boolean existsByLicenseNumberIgnoreCaseAndIdNot(String licenseNumber, Long id);

    /**
     * Doctor directory search.
     *
     * @param publicOnly true = only active doctors in active departments (patient/public view);
     *                   false = admin view, where {@code active} filters by account status and the
     *                   free-text search also matches e-mail and licence number
     */
    @EntityGraph(attributePaths = {"user", "department"})
    @Query("""
            SELECT d FROM Doctor d JOIN d.user u JOIN d.department dep
            WHERE (:publicOnly = false OR (u.active = true AND dep.active = true))
              AND (:active IS NULL OR u.active = :active)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
              AND (:specialization IS NULL OR LOWER(d.specialization) = LOWER(:specialization))
              AND (:search IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(dep.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR (:publicOnly = false AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                                               OR LOWER(d.licenseNumber) LIKE LOWER(CONCAT('%', :search, '%')))))
            """)
    Page<Doctor> search(@Param("publicOnly") boolean publicOnly,
                        @Param("active") Boolean active,
                        @Param("departmentId") Long departmentId,
                        @Param("specialization") String specialization,
                        @Param("search") String search,
                        Pageable pageable);

    @Query("""
            SELECT DISTINCT d.specialization FROM Doctor d
            WHERE d.user.active = true AND d.department.active = true
            ORDER BY d.specialization
            """)
    List<String> findActiveSpecializations();

    /** [departmentId, number of active doctors] pairs. */
    @Query("SELECT d.department.id, COUNT(d) FROM Doctor d WHERE d.user.active = true GROUP BY d.department.id")
    List<Object[]> countActiveByDepartment();

    @Query("SELECT COUNT(d) FROM Doctor d WHERE d.department.id = :departmentId AND d.user.active = true")
    long countActiveInDepartment(@Param("departmentId") Long departmentId);
}
