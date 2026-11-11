package com.medicare.repository;

import com.medicare.entity.LabReport;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LabReportRepository extends JpaRepository<LabReport, Long> {
}
