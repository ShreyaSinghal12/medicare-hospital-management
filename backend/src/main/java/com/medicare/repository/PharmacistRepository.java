package com.medicare.repository;

import java.util.Optional;

import com.medicare.entity.Pharmacist;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacistRepository extends JpaRepository<Pharmacist, Long> {

    Optional<Pharmacist> findByUserId(Long userId);
}
