package com.medicare.repository;

import com.medicare.entity.HospitalSettings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalSettingsRepository extends JpaRepository<HospitalSettings, Long> {
}
