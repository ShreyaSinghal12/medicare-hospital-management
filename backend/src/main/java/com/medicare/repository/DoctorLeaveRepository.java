package com.medicare.repository;

import java.time.LocalDate;
import java.util.List;

import com.medicare.entity.DoctorLeave;
import com.medicare.enums.LeaveStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {

    /** Approved leave/unavailability touching the date range (used by slot generation). */
    @Query("""
            SELECT l FROM DoctorLeave l
            WHERE l.doctor.id = :doctorId AND l.status = com.medicare.enums.LeaveStatus.APPROVED
              AND l.startDate <= :to AND l.endDate >= :from
            """)
    List<DoctorLeave> findApprovedOverlapping(@Param("doctorId") Long doctorId, @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    List<DoctorLeave> findByDoctorIdOrderByStartDateDesc(Long doctorId);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    Page<DoctorLeave> findByStatusOrderByStartDateAsc(LeaveStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    Page<DoctorLeave> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
