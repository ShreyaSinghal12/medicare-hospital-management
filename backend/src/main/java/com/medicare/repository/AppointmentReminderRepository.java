package com.medicare.repository;

import com.medicare.entity.AppointmentReminder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentReminderRepository extends JpaRepository<AppointmentReminder, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<AppointmentReminder> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
