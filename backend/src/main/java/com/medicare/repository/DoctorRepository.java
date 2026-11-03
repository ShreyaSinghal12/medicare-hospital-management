package com.medicare.repository;

import java.util.Optional;

import com.medicare.entity.Doctor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Doctor> findByUserId(Long userId);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);
}
