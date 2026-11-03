package com.medicare.repository;

import java.util.List;

import com.medicare.entity.AppointmentStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {

    List<AppointmentStatusHistory> findByAppointmentIdOrderByChangedAtAscIdAsc(Long appointmentId);
}
