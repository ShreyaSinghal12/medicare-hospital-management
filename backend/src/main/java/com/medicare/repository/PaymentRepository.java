package com.medicare.repository;

import java.time.Instant;
import java.util.List;

import com.medicare.entity.Payment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"recordedBy"})
    List<Payment> findByInvoiceIdOrderByPaidAtAscIdAsc(Long invoiceId);

    /** [type, method, sum(amount)] of payments and refunds made in the range. */
    @Query("""
            SELECT p.type, p.method, COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.paidAt >= :fromTime AND p.paidAt < :toTime GROUP BY p.type, p.method
            """)
    List<Object[]> totalsByMethod(@Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime);
}
