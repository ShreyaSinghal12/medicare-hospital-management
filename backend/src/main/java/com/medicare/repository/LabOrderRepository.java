package com.medicare.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.medicare.entity.LabOrder;
import com.medicare.enums.LabOrderStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "doctor.department",
            "consultation", "consultation.appointment", "report", "report.uploadedBy"})
    Optional<LabOrder> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "report"})
    List<LabOrder> findByConsultationIdOrderByOrderedAtDesc(Long consultationId);

    /** Orders of a patient or of an ordering doctor; text matches order number, patient name/code or a test name. */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "report"})
    @Query("""
            SELECT o FROM LabOrder o
            WHERE (:patientId IS NULL OR o.patient.id = :patientId)
              AND (:doctorId IS NULL OR o.doctor.id = :doctorId)
              AND o.status IN :statuses
              AND (:search IS NULL
                   OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(o.patient.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(o.patient.patientCode) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (SELECT 1 FROM LabOrderItem i WHERE i.order = o
                              AND LOWER(i.testName) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<LabOrder> search(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId,
                          @Param("statuses") Collection<LabOrderStatus> statuses,
                          @Param("search") String search, Pageable pageable);

    /** Locks the order row (SELECT ... FOR UPDATE) so concurrent changes to one order run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM LabOrder o WHERE o.id = :id")
    Optional<LabOrder> lockById(@Param("id") Long id);

    long countByDoctorIdAndStatusIn(Long doctorId, Collection<LabOrderStatus> statuses);
}
