package com.medicare.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.medicare.entity.MedicineBatch;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    List<MedicineBatch> findByMedicineIdOrderByExpiryDateAscIdAsc(Long medicineId);

    boolean existsByMedicineIdAndBatchNumberIgnoreCase(Long medicineId, String batchNumber);

    /** SELECT ... FOR UPDATE: stock changes on one batch run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"medicine"})
    @Query("SELECT b FROM MedicineBatch b WHERE b.id = :id")
    Optional<MedicineBatch> lockById(@Param("id") Long id);

    /**
     * Per medicine: [medicineId, usable stock (not expired), expired stock, nearest usable expiry].
     * "Expired" means the expiry date is before {@code today}.
     */
    @Query("""
            SELECT b.medicine.id,
                   SUM(CASE WHEN b.expiryDate >= :today THEN b.quantityAvailable ELSE 0 END),
                   SUM(CASE WHEN b.expiryDate < :today THEN b.quantityAvailable ELSE 0 END),
                   MIN(CASE WHEN b.expiryDate >= :today AND b.quantityAvailable > 0 THEN b.expiryDate ELSE NULL END)
            FROM MedicineBatch b
            WHERE b.medicine.id IN :medicineIds
            GROUP BY b.medicine.id
            """)
    List<Object[]> stockTotals(@Param("medicineIds") Collection<Long> medicineIds, @Param("today") LocalDate today);

    @EntityGraph(attributePaths = {"medicine"})
    @Query("""
            SELECT b FROM MedicineBatch b
            WHERE b.quantityAvailable > 0 AND b.expiryDate >= :from AND b.expiryDate <= :to
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<MedicineBatch> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"medicine"})
    @Query("SELECT b FROM MedicineBatch b WHERE b.quantityAvailable > 0 AND b.expiryDate < :today ORDER BY b.expiryDate ASC")
    List<MedicineBatch> findExpiredWithStock(@Param("today") LocalDate today);

    /** Usable batches (not expired, stock left) of the given medicines, earliest expiry first. */
    @Query("""
            SELECT b FROM MedicineBatch b
            WHERE b.medicine.id IN :medicineIds AND b.quantityAvailable > 0 AND b.expiryDate >= :today
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<MedicineBatch> findUsable(@Param("medicineIds") Collection<Long> medicineIds, @Param("today") LocalDate today);
}
