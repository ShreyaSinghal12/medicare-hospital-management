package com.medicare.repository;

import java.util.List;
import java.util.Optional;

import com.medicare.entity.PatientAllergy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {

    List<PatientAllergy> findByPatientIdOrderByActiveDescCreatedAtDesc(Long patientId);

    Optional<PatientAllergy> findByIdAndPatientId(Long id, Long patientId);

    boolean existsByPatientIdAndAllergenIgnoreCaseAndActiveTrue(Long patientId, String allergen);
}
