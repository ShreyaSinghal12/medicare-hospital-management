package com.medicare.repository;

import java.util.Collection;
import java.util.List;

import com.medicare.entity.Appointment;
import com.medicare.entity.DispensingItem;
import com.medicare.entity.LabOrderItem;
import com.medicare.enums.AppointmentStatus;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries for the services a patient can be billed for. */
public interface BillableSourceRepository extends Repository<Appointment, Long> {

    @Query("""
            SELECT a FROM Appointment a JOIN FETCH a.doctor d JOIN FETCH d.user JOIN FETCH d.department
            WHERE a.patient.id = :patientId AND a.status IN :statuses
            ORDER BY a.appointmentDate DESC, a.startTime DESC
            """)
    List<Appointment> appointments(@Param("patientId") Long patientId, @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("""
            SELECT i FROM LabOrderItem i JOIN FETCH i.order o
            WHERE o.patient.id = :patientId AND o.status <> com.medicare.enums.LabOrderStatus.CANCELLED
            ORDER BY o.orderedAt DESC, i.id ASC
            """)
    List<LabOrderItem> labItems(@Param("patientId") Long patientId);

    @Query("""
            SELECT i FROM DispensingItem i JOIN FETCH i.record r JOIN FETCH i.medicine
            WHERE r.patient.id = :patientId
            ORDER BY r.dispensedAt DESC, i.id ASC
            """)
    List<DispensingItem> dispensingItems(@Param("patientId") Long patientId);
}
