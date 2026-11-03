package com.medicare.repository;

import java.util.Optional;

import com.medicare.entity.Patient;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<Patient> findByUserId(Long userId);

    Optional<Patient> findByPatientCode(String patientCode);
}
