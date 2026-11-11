package com.medicare.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.medicare.entity.Invoice;
import com.medicare.enums.InvoiceStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"patient", "patient.user", "issuedBy"})
    Optional<Invoice> findWithDetailsById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"patient", "patient.user"})
    @Query("""
            SELECT i FROM Invoice i
            WHERE (:patientId IS NULL OR i.patient.id = :patientId)
              AND i.status IN :statuses
              AND (:fromTime IS NULL OR i.issuedAt >= :fromTime)
              AND (:toTime IS NULL OR i.issuedAt < :toTime)
              AND (:search IS NULL
                   OR LOWER(i.invoiceNo) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.patient.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.patient.patientCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Invoice> search(@Param("patientId") Long patientId, @Param("statuses") Collection<InvoiceStatus> statuses,
                         @Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime,
                         @Param("search") String search, Pageable pageable);

    /** [status, count, sum(total)] of invoices issued in the range. */
    @Query("""
            SELECT i.status, COUNT(i), COALESCE(SUM(i.totalAmount), 0) FROM Invoice i
            WHERE i.issuedAt >= :fromTime AND i.issuedAt < :toTime GROUP BY i.status
            """)
    List<Object[]> statusTotals(@Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime);

    /** [item type, sum(line total)] for non-cancelled invoices issued in the range. */
    @Query("""
            SELECT it.type, COALESCE(SUM(it.lineTotal), 0) FROM InvoiceItem it
            WHERE it.invoice.status <> com.medicare.enums.InvoiceStatus.CANCELLED
              AND it.invoice.issuedAt >= :fromTime AND it.invoice.issuedAt < :toTime
            GROUP BY it.type
            """)
    List<Object[]> itemTypeTotals(@Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime);

    /** [count, sum(total - paid)] of all open invoices (any issue date). */
    @Query("""
            SELECT COUNT(i), COALESCE(SUM(i.totalAmount - i.amountPaid), 0) FROM Invoice i
            WHERE i.status IN (com.medicare.enums.InvoiceStatus.UNPAID, com.medicare.enums.InvoiceStatus.PARTIALLY_PAID)
            """)
    List<Object[]> outstanding();

    @Query("SELECT COALESCE(SUM(i.totalAmount - i.amountPaid), 0) FROM Invoice i WHERE i.patient.id = :patientId "
            + "AND i.status IN (com.medicare.enums.InvoiceStatus.UNPAID, com.medicare.enums.InvoiceStatus.PARTIALLY_PAID)")
    BigDecimal outstandingForPatient(@Param("patientId") Long patientId);
}
