package com.medicare.repository;

import java.util.Optional;

import com.medicare.entity.Receptionist;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceptionistRepository extends JpaRepository<Receptionist, Long> {

    Optional<Receptionist> findByUserId(Long userId);
}
