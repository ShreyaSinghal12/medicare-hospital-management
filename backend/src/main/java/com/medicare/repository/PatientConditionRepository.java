package com.medicare.repository;

import java.util.List;
import java.util.Optional;

import com.medicare.entity.PatientCondition;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientConditionRepository extends JpaRepository<PatientCondition, Long> {

    List<PatientCondition> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    Optional<PatientCondition> findByIdAndPatientId(Long id, Long patientId);
}
