package com.medicare.repository;

import java.util.List;
import java.util.Optional;

import com.medicare.entity.DoctorAvailability;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    List<DoctorAvailability> findByDoctorIdOrderByDayOfWeek(Long doctorId);

    Optional<DoctorAvailability> findByDoctorIdAndDayOfWeekAndActiveTrue(Long doctorId, int dayOfWeek);
}
