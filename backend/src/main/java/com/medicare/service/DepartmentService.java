package com.medicare.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.medicare.dto.department.DepartmentRequest;
import com.medicare.dto.department.DepartmentResponse;
import com.medicare.entity.Department;
import com.medicare.enums.AuditOutcome;
import com.medicare.exception.BusinessRuleException;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.repository.DepartmentRepository;
import com.medicare.repository.DoctorRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department administration. Departments are never deleted (doctors and historic
 * appointments reference them); they are deactivated instead, which hides them and
 * their doctors from patients.
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final AuditService auditService;

    public DepartmentService(DepartmentRepository departmentRepository, DoctorRepository doctorRepository,
                             AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listAll() {
        Map<Long, Long> counts = activeDoctorCounts();
        return departmentRepository.findAll(Sort.by("name")).stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listActive() {
        Map<Long, Long> counts = activeDoctorCounts();
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest req) {
        String name = req.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("DEPARTMENT_EXISTS", "A department named '" + name + "' already exists");
        }
        Department saved = departmentRepository.save(new Department(name, blankToNull(req.description())));
        auditService.record(AuditService.Actions.DEPARTMENT_CREATED, "Department", saved.getId(), AuditOutcome.SUCCESS, name);
        return toResponse(saved, 0);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest req) {
        Department dept = find(id);
        String name = req.name().trim();
        departmentRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("DEPARTMENT_EXISTS", "A department named '" + name + "' already exists");
                });
        dept.setName(name);
        dept.setDescription(blankToNull(req.description()));
        departmentRepository.flush();
        auditService.record(AuditService.Actions.DEPARTMENT_UPDATED, "Department", id, AuditOutcome.SUCCESS, name);
        return toResponse(dept, doctorRepository.countActiveInDepartment(id));
    }

    /** A department with active doctors can't be deactivated; reassign or deactivate the doctors first. */
    @Transactional
    public DepartmentResponse setActive(Long id, boolean active) {
        Department dept = find(id);
        long activeDoctors = doctorRepository.countActiveInDepartment(id);
        if (!active && activeDoctors > 0) {
            throw new BusinessRuleException("DEPARTMENT_HAS_ACTIVE_DOCTORS",
                    "This department still has " + activeDoctors + " active doctor(s). Reassign or deactivate them first.");
        }
        if (dept.isActive() != active) {
            dept.setActive(active);
            auditService.record(active ? AuditService.Actions.DEPARTMENT_ACTIVATED : AuditService.Actions.DEPARTMENT_DEACTIVATED,
                    "Department", id, AuditOutcome.SUCCESS, dept.getName());
        }
        return toResponse(dept, activeDoctors);
    }

    Department find(Long id) {
        return departmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private Map<Long, Long> activeDoctorCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : doctorRepository.countActiveByDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static DepartmentResponse toResponse(Department d, long count) {
        return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.isActive(), count);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
