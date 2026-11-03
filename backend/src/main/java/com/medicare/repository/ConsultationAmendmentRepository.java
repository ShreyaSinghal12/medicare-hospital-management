package com.medicare.repository;

import java.util.List;

import com.medicare.entity.ConsultationAmendment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationAmendmentRepository extends JpaRepository<ConsultationAmendment, Long> {

    List<ConsultationAmendment> findByConsultationIdOrderByCreatedAtDescIdDesc(Long consultationId);
}
