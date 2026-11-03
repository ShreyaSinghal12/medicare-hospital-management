package com.medicare.repository;

import java.util.Optional;

import com.medicare.entity.LabTechnician;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTechnicianRepository extends JpaRepository<LabTechnician, Long> {

    Optional<LabTechnician> findByUserId(Long userId);
}
