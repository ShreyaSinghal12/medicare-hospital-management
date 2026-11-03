package com.medicare.service;

import com.medicare.entity.Doctor;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.Role;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that decides whether a doctor may see or write a patient's clinical
 * record. Rule: the doctor must have a <b>treatment relationship</b> with the patient,
 * i.e. at least one non-cancelled appointment together (past, current or upcoming).
 *
 * <p>Receptionists, administrators, lab and pharmacy staff never get clinical record
 * access through this service. Every granted access and every denial is audit-logged.</p>
 */
@Service
public class ClinicalAccessService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;

    public ClinicalAccessService(DoctorRepository doctorRepository, AppointmentRepository appointmentRepository,
                                 AuditService auditService) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
    }

    /** The doctor profile of the acting user (403 for every other role). */
    @Transactional(readOnly = true)
    public Doctor requireDoctor(AppUserPrincipal actor) {
        if (actor.role() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can access clinical records");
        }
        return doctorRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for the current user"));
    }

    public boolean hasTreatmentRelationship(Long doctorId, Long patientId) {
        return appointmentRepository.existsByDoctorIdAndPatientIdAndStatusNot(doctorId, patientId, AppointmentStatus.CANCELLED);
    }

    /**
     * Grants access or throws 403. On success records {@code action} (e.g. PATIENT_RECORD_VIEWED)
     * in the audit log within the caller's transaction; denials are recorded independently.
     */
    @Transactional
    public Doctor requireTreatingDoctor(AppUserPrincipal actor, Long patientId, String action) {
        Doctor doctor;
        try {
            doctor = requireDoctor(actor);
        } catch (AccessDeniedException ex) {
            auditService.recordIndependently("CLINICAL_ACCESS_DENIED", "Patient", patientId, AuditOutcome.DENIED,
                    "Role " + actor.role() + " attempted " + action);
            throw ex;
        }
        if (!hasTreatmentRelationship(doctor.getId(), patientId)) {
            auditService.recordIndependently("CLINICAL_ACCESS_DENIED", "Patient", patientId, AuditOutcome.DENIED,
                    "No treatment relationship (" + action + ")");
            throw new AccessDeniedException("You can only access records of patients you are treating");
        }
        auditService.record(action, "Patient", patientId, AuditOutcome.SUCCESS, null);
        return doctor;
    }
}
