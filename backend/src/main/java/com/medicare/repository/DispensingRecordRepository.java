package com.medicare.repository;

import java.util.List;
import java.util.Optional;

import com.medicare.entity.DispensingRecord;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispensingRecordRepository extends JpaRepository<DispensingRecord, Long> {

    @EntityGraph(attributePaths = {"prescription", "prescription.doctor", "prescription.doctor.user", "patient",
            "patient.user", "dispensedBy"})
    Optional<DispensingRecord> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"prescription", "patient", "patient.user", "dispensedBy"})
    List<DispensingRecord> findByPrescriptionIdOrderByDispensedAtDesc(Long prescriptionId);

    @EntityGraph(attributePaths = {"prescription", "patient", "patient.user", "dispensedBy"})
    @Query("""
            SELECT r FROM DispensingRecord r
            WHERE :search IS NULL
               OR LOWER(r.dispensingNo) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.prescription.prescriptionNo) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.patient.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.patient.patientCode) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<DispensingRecord> search(@Param("search") String search, Pageable pageable);

    /** [prescriptionItemId, total quantity dispensed] for one prescription version. */
    @Query("""
            SELECT i.prescriptionItem.id, SUM(i.quantity) FROM DispensingItem i
            WHERE i.record.prescription.id = :prescriptionId
            GROUP BY i.prescriptionItem.id
            """)
    List<Object[]> dispensedPerItem(@Param("prescriptionId") Long prescriptionId);
}
