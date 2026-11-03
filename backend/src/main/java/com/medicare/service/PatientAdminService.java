package com.medicare.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.patient.AdminUpdatePatientRequest;
import com.medicare.dto.patient.DuplicatePatientGroup;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.entity.Patient;
import com.medicare.enums.AuditOutcome;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.PatientMapper;
import com.medicare.repository.PatientRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient administration with least privilege: only demographic/account data is
 * visible or editable here. Diagnoses, prescriptions and results are never exposed.
 */
@Service
public class PatientAdminService {

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public PatientAdminService(PatientRepository patientRepository, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientProfileResponse> search(String search, Boolean active, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("user.fullName"));
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(patientRepository.search(term, active, pageable), PatientMapper::toProfile);
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse get(Long id) {
        return PatientMapper.toProfile(find(id));
    }

    /** Non-clinical correction; the reason is stored in the audit log. */
    @Transactional
    public PatientProfileResponse correct(Long id, AdminUpdatePatientRequest req) {
        Patient p = find(id);
        p.getUser().setFullName(req.fullName().trim());
        p.getUser().setPhone(req.phone().trim());
        p.setDateOfBirth(req.dateOfBirth());
        p.setGender(req.gender());
        p.setAddress(blankToNull(req.address()));
        p.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        p.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        patientRepository.flush();
        auditService.record(AuditService.Actions.PATIENT_RECORD_CORRECTED, "Patient", id, AuditOutcome.SUCCESS,
                "Reason: " + req.reason().trim());
        return PatientMapper.toProfile(p);
    }

    /**
     * Possible duplicate registrations: same name (case-insensitive) and date of birth,
     * or same phone number (digits only, last 10 digits compared).
     */
    @Transactional(readOnly = true)
    public List<DuplicatePatientGroup> findDuplicates() {
        var candidates = patientRepository.findDuplicateCandidates();
        List<Map.Entry<String, List<Long>>> groups = new ArrayList<>();

        Map<String, List<Long>> byNameDob = candidates.stream().collect(Collectors.groupingBy(
                c -> c.getFullName().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " | " + c.getDateOfBirth(),
                LinkedHashMap::new, Collectors.mapping(PatientRepository.DuplicateCandidate::getId, Collectors.toList())));
        byNameDob.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .forEach(e -> groups.add(Map.entry("NAME_AND_DATE_OF_BIRTH|" + e.getKey(), e.getValue())));

        Map<String, List<Long>> byPhone = candidates.stream()
                .filter(c -> c.getPhone() != null && c.getPhone().replaceAll("\\D", "").length() >= 7)
                .collect(Collectors.groupingBy(c -> lastDigits(c.getPhone()), LinkedHashMap::new,
                        Collectors.mapping(PatientRepository.DuplicateCandidate::getId, Collectors.toList())));
        byPhone.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .forEach(e -> groups.add(Map.entry("PHONE|" + e.getKey(), e.getValue())));

        var allIds = groups.stream().flatMap(g -> g.getValue().stream()).collect(Collectors.toSet());
        Map<Long, PatientProfileResponse> byId = patientRepository.findWithUserByIdIn(allIds).stream()
                .map(PatientMapper::toProfile)
                .collect(Collectors.toMap(PatientProfileResponse::id, Function.identity()));

        return groups.stream().map(g -> {
            String[] parts = g.getKey().split("\\|", 2);
            List<PatientProfileResponse> patients = g.getValue().stream().map(byId::get)
                    .sorted(Comparator.comparing(PatientProfileResponse::patientCode)).toList();
            return new DuplicatePatientGroup(parts[0], parts[1], patients);
        }).toList();
    }

    private Patient find(Long id) {
        return patientRepository.findWithUserById(id).orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private static String lastDigits(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
