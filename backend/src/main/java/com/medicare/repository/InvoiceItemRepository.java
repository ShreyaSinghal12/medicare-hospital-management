package com.medicare.repository;

import java.util.List;

import com.medicare.entity.InvoiceItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    /** Keys like {@code LAB_TEST:17} of charges already on an active invoice of this patient. */
    @Query("SELECT i.billedReference FROM InvoiceItem i WHERE i.invoice.patient.id = :patientId AND i.billedReference IS NOT NULL")
    List<String> billedReferences(@Param("patientId") Long patientId);
}
