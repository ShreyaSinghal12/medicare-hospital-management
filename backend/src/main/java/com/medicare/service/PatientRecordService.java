package com.medicare.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.medicare.dto.clinical.AllergyRequest;
import com.medicare.dto.clinical.AllergyResponse;
import com.medicare.dto.clinical.ConditionRequest;
import com.medicare.dto.clinical.ConditionResponse;
import com.medicare.dto.clinical.MedicalHistoryResponse;
import com.medicare.dto.clinical.PatientRecordResponse;
import com.medicare.dto.common.PageResponse;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.entity.PatientAllergy;
import com.medicare.entity.PatientCondition;
import com.medicare.enums.ConditionStatus;
import com.medicare.enums.ConsultationStatus;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.ClinicalMapper;
import com.medicare.mapper.PatientMapper;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.PatientAllergyRepository;
import com.medicare.repository.PatientConditionRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.PrescriptionRepository;
import com.medicare.security.AppUserPrincipal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Patient clinical record for treating doctors, and the patient's own medical history. */
@Service
public class PatientRecordService {

    private final PatientRepository patientRepository;
    private final PatientAllergyRepository allergyRepository;
    private final PatientConditionRepository conditionRepository;
    private final ConsultationRepository consultationRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final ClinicalAccessService accessService;
    private final PrescriptionRepository prescriptionRepository;

    public PatientRecordService(PatientRepository patientRepository, PatientAllergyRepository allergyRepository,
                                PatientConditionRepository conditionRepository,
                                ConsultationRepository consultationRepository,
                                AppointmentRepository appointmentRepository, DoctorRepository doctorRepository,
                                ClinicalAccessService accessService, PrescriptionRepository prescriptionRepository) {
        this.patientRepository = patientRepository;
        this.allergyRepository = allergyRepository;
        this.conditionRepository = conditionRepository;
        this.consultationRepository = consultationRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.accessService = accessService;
        this.prescriptionRepository = prescriptionRepository;
    }

    // ================================================================ doctor views

    /** Patients the doctor has a treatment relationship with (demographics only). */
    @Transactional(readOnly = true)
    public PageResponse<PatientProfileResponse> myPatients(AppUserPrincipal actor, String search, int page, int size) {
        Doctor doctor = accessService.requireDoctor(actor);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("user.fullName"));
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(patientRepository.findPatientsOfDoctor(doctor.getId(), term, pageable),
                PatientMapper::toProfile);
    }

    /** Full record for a treating doctor. The access is audit-logged. */
    @Transactional
    public PatientRecordResponse record(AppUserPrincipal actor, Long patientId) {
        Patient patient = findPatient(patientId);
        Doctor doctor = accessService.requireTreatingDoctor(actor, patientId, "PATIENT_RECORD_VIEWED");
        var consultations = consultationRepository
                .findByPatientIdOrderByAppointmentAppointmentDateDescAppointmentStartTimeDesc(patientId).stream()
                .filter(c -> c.getStatus() == ConsultationStatus.FINALIZED || c.getDoctor().getId().equals(doctor.getId()))
                .map(c -> ClinicalMapper.toSummary(c, doctor.getId()))
                .toList();
        var visits = appointmentRepository.findByPatientIdOrderByAppointmentDateDescStartTimeDesc(patientId).stream()
                .map(ClinicalMapper::toVisit).toList();
        return new PatientRecordResponse(PatientMapper.toProfile(patient), allergies(patientId, false),
                conditions(patientId), consultations, visits);
    }

    @Transactional
    public AllergyResponse addAllergy(AppUserPrincipal actor, Long patientId, AllergyRequest req) {
        findPatient(patientId);
        Doctor doctor = accessService.requireTreatingDoctor(actor, patientId, "ALLERGY_RECORDED");
        String allergen = req.allergen().trim();
        if (allergyRepository.existsByPatientIdAndAllergenIgnoreCaseAndActiveTrue(patientId, allergen)) {
            throw new ConflictException("ALLERGY_EXISTS", "This allergy is already recorded");
        }
        PatientAllergy a = allergyRepository.save(new PatientAllergy(patientId, allergen, blankToNull(req.reaction()),
                req.severity(), doctor.getId()));
        return ClinicalMapper.toAllergy(a, doctor.getUser().getFullName());
    }

    @Transactional
    public AllergyResponse setAllergyActive(AppUserPrincipal actor, Long patientId, Long allergyId, boolean active) {
        accessService.requireTreatingDoctor(actor, patientId, active ? "ALLERGY_REACTIVATED" : "ALLERGY_DEACTIVATED");
        PatientAllergy a = allergyRepository.findByIdAndPatientId(allergyId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Allergy", allergyId));
        a.setActive(active);
        return ClinicalMapper.toAllergy(a, doctorName(a.getRecordedByDoctorId()));
    }

    @Transactional
    public ConditionResponse addCondition(AppUserPrincipal actor, Long patientId, ConditionRequest req) {
        findPatient(patientId);
        Doctor doctor = accessService.requireTreatingDoctor(actor, patientId, "CONDITION_RECORDED");
        PatientCondition c = conditionRepository.save(new PatientCondition(patientId, req.conditionName().trim(),
                req.status(), req.diagnosedOn(), blankToNull(req.notes()), doctor.getId()));
        return ClinicalMapper.toCondition(c, doctor.getUser().getFullName());
    }

    @Transactional
    public ConditionResponse setConditionStatus(AppUserPrincipal actor, Long patientId, Long conditionId, ConditionStatus status) {
        accessService.requireTreatingDoctor(actor, patientId, "CONDITION_UPDATED");
        PatientCondition c = conditionRepository.findByIdAndPatientId(conditionId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Condition", conditionId));
        c.setStatus(status);
        return ClinicalMapper.toCondition(c, doctorName(c.getRecordedByDoctorId()));
    }

    // ================================================================ patient view

    /** The logged-in patient's own history: active allergies, conditions, finalized consultations. */
    @Transactional(readOnly = true)
    public MedicalHistoryResponse myHistory(AppUserPrincipal actor) {
        Patient patient = patientRepository.findByUserId(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
        var consultations = consultationRepository
                .findByPatientIdAndStatusOrderByAppointmentAppointmentDateDescAppointmentStartTimeDesc(patient.getId(),
                        ConsultationStatus.FINALIZED);
        Map<Long, List<MedicalHistoryResponse.PrescriptionRef>> rx = new HashMap<>();
        if (!consultations.isEmpty()) {
            prescriptionRepository.findCurrentForConsultations(consultations.stream().map(c -> c.getId()).toList())
                    .forEach(p -> rx.computeIfAbsent(p.getConsultation().getId(), k -> new java.util.ArrayList<>())
                            .add(new MedicalHistoryResponse.PrescriptionRef(p.getId(), p.getPrescriptionNo(),
                                    p.getVersionNumber(), p.getItems().stream().map(i -> i.getMedicineName()).toList())));
        }
        var timeline = consultations.stream()
                .map(c -> ClinicalMapper.toTimeline(c, rx.getOrDefault(c.getId(), List.of()))).toList();
        return new MedicalHistoryResponse(allergies(patient.getId(), true), conditions(patient.getId()), timeline);
    }

    // ================================================================ helpers

    private List<AllergyResponse> allergies(Long patientId, boolean activeOnly) {
        Map<Long, String> names = new HashMap<>();
        return allergyRepository.findByPatientIdOrderByActiveDescCreatedAtDesc(patientId).stream()
                .filter(a -> !activeOnly || a.isActive())
                .map(a -> ClinicalMapper.toAllergy(a, names.computeIfAbsent(a.getRecordedByDoctorId(), this::doctorName)))
                .toList();
    }

    private List<ConditionResponse> conditions(Long patientId) {
        Map<Long, String> names = new HashMap<>();
        return conditionRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(c -> ClinicalMapper.toCondition(c, names.computeIfAbsent(c.getRecordedByDoctorId(), this::doctorName)))
                .toList();
    }

    private String doctorName(Long doctorId) {
        if (doctorId == null) {
            return null;
        }
        return doctorRepository.findWithDetailsById(doctorId).map(d -> d.getUser().getFullName()).orElse(null);
    }

    private Patient findPatient(Long id) {
        return patientRepository.findWithUserById(id).orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
