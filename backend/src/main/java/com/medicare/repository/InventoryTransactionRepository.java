package com.medicare.repository;

import com.medicare.entity.InventoryTransaction;
import com.medicare.enums.InventoryTxnType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @EntityGraph(attributePaths = {"medicine", "batch", "performedBy"})
    @Query("""
            SELECT t FROM InventoryTransaction t
            WHERE (:medicineId IS NULL OR t.medicine.id = :medicineId)
              AND (:type IS NULL OR t.type = :type)
            """)
    Page<InventoryTransaction> search(@Param("medicineId") Long medicineId, @Param("type") InventoryTxnType type,
                                      Pageable pageable);
}
