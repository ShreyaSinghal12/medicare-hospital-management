package com.medicare.service;

import java.util.List;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.doctor.AdminDoctorResponse;
import com.medicare.dto.doctor.DoctorDetailResponse;
import com.medicare.dto.doctor.DoctorRequest;
import com.medicare.dto.doctor.DoctorSelfUpdateRequest;
import com.medicare.dto.doctor.DoctorSummaryResponse;
import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.Role;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.DoctorMapper;
import com.medicare.repository.DoctorRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public doctor directory, admin doctor management and the doctor's own profile. */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentService departmentService;
    private final StaffAccountService staffAccountService;
    private final AuthService authService;
    private final AuditService auditService;

    public DoctorService(DoctorRepository doctorRepository, DepartmentService departmentService,
                         StaffAccountService staffAccountService, AuthService authService, AuditService auditService) {
        this.doctorRepository = doctorRepository;
        this.departmentService = departmentService;
        this.staffAccountService = staffAccountService;
        this.authService = authService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------- public directory

    @Transactional(readOnly = true)
    public PageResponse<DoctorSummaryResponse> searchPublic(String search, Long departmentId, String specialization,
                                                            int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50), Sort.by("user.fullName"));
        return PageResponse.from(doctorRepository.search(true, null, departmentId, blankToNull(specialization),
                blankToNull(search), pageable), DoctorMapper::toSummary);
    }

    /** Inactive doctors (or doctors of inactive departments) are reported as not found to the public. */
    @Transactional(readOnly = true)
    public DoctorDetailResponse getPublic(Long id) {
        Doctor d = doctorRepository.findWithDetailsById(id)
                .filter(doc -> doc.getUser().isActive() && doc.getDepartment().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
        return DoctorMapper.toDetail(d);
    }

    @Transactional(readOnly = true)
    public List<String> specializations() {
        return doctorRepository.findActiveSpecializations();
    }

    // ------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public PageResponse<AdminDoctorResponse> searchAdmin(String search, Long departmentId, Boolean active,
                                                         int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("user.fullName"));
        return PageResponse.from(doctorRepository.search(false, active, departmentId, null, blankToNull(search), pageable),
                DoctorMapper::toAdmin);
    }

    @Transactional(readOnly = true)
    public AdminDoctorResponse getAdmin(Long id) {
        return DoctorMapper.toAdmin(find(id));
    }

    @Transactional
    public AdminDoctorResponse create(DoctorRequest req) {
        if (req.email() == null || req.email().isBlank()) {
            throw new BusinessRuleException("EMAIL_REQUIRED", "E-mail is required when creating a doctor");
        }
        if (doctorRepository.existsByLicenseNumberIgnoreCase(req.licenseNumber().trim())) {
            throw new ConflictException("LICENSE_ALREADY_REGISTERED", "A doctor with this licence number already exists");
        }
        Department dept = activeDepartment(req.departmentId());
        User user = staffAccountService.createStaffUser(req.email(), req.fullName(), req.phone(), Role.DOCTOR);
        Doctor doctor = new Doctor(user, dept, req.specialization().trim(), req.qualifications().trim(),
                req.licenseNumber().trim().toUpperCase(), req.experienceYears(), req.consultationFee(),
                req.slotDurationMinutes());
        doctor.setBio(blankToNull(req.bio()));
        doctorRepository.save(doctor);
        authService.sendAccountSetupLink(user);
        auditService.record(AuditService.Actions.DOCTOR_CREATED, "Doctor", doctor.getId(), AuditOutcome.SUCCESS,
                "Department: " + dept.getName());
        return DoctorMapper.toAdmin(doctor);
    }

    /**
     * Updates professional details. Changing the slot duration affects only slots generated
     * from now on; existing bookings keep their times (handled in Phase 4 scheduling).
     */
    @Transactional
    public AdminDoctorResponse update(Long id, DoctorRequest req) {
        Doctor doctor = find(id);
        if (doctorRepository.existsByLicenseNumberIgnoreCaseAndIdNot(req.licenseNumber().trim(), id)) {
            throw new ConflictException("LICENSE_ALREADY_REGISTERED", "A doctor with this licence number already exists");
        }
        if (!doctor.getDepartment().getId().equals(req.departmentId())) {
            doctor.setDepartment(activeDepartment(req.departmentId()));
        }
        User user = doctor.getUser();
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone().trim());
        doctor.setSpecialization(req.specialization().trim());
        doctor.setQualifications(req.qualifications().trim());
        doctor.setLicenseNumber(req.licenseNumber().trim().toUpperCase());
        doctor.setExperienceYears(req.experienceYears());
        doctor.setConsultationFee(req.consultationFee());
        doctor.setSlotDurationMinutes(req.slotDurationMinutes());
        doctor.setBio(blankToNull(req.bio()));
        doctorRepository.flush();
        auditService.record(AuditService.Actions.DOCTOR_UPDATED, "Doctor", id, AuditOutcome.SUCCESS, null);
        return DoctorMapper.toAdmin(doctor);
    }

    // ------------------------------------------------------------- doctor self-service

    @Transactional(readOnly = true)
    public AdminDoctorResponse getOwn(Long userId) {
        return DoctorMapper.toAdmin(findByUser(userId));
    }

    @Transactional
    public AdminDoctorResponse updateOwn(Long userId, DoctorSelfUpdateRequest req) {
        Doctor doctor = findByUser(userId);
        doctor.getUser().setPhone(req.phone().trim());
        doctor.setQualifications(req.qualifications().trim());
        doctor.setBio(blankToNull(req.bio()));
        doctorRepository.flush();
        auditService.record(AuditService.Actions.DOCTOR_PROFILE_UPDATED, "Doctor", doctor.getId(), AuditOutcome.SUCCESS, null);
        return DoctorMapper.toAdmin(doctor);
    }

    // ------------------------------------------------------------- helpers

    private Doctor find(Long id) {
        return doctorRepository.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
    }

    private Doctor findByUser(Long userId) {
        return doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for the current user"));
    }

    private Department activeDepartment(Long id) {
        Department dept = departmentService.find(id);
        if (!dept.isActive()) {
            throw new BusinessRuleException("DEPARTMENT_INACTIVE", "Doctors can't be assigned to an inactive department");
        }
        return dept;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
