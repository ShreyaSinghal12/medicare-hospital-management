package com.medicare.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import com.medicare.entity.AppointmentSlot;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AppointmentSlot s WHERE s.doctorId = :doctorId AND s.slotDate = :date AND s.startTime = :start")
    Optional<AppointmentSlot> findForUpdate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date,
                                            @Param("start") LocalTime start);
}
