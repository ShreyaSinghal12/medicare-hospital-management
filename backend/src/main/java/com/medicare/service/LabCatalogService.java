package com.medicare.service;

import java.util.List;

import com.medicare.dto.lab.LabTestRequest;
import com.medicare.dto.lab.LabTestResponse;
import com.medicare.entity.LabTest;
import com.medicare.enums.AuditOutcome;
import com.medicare.exception.ConflictException;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.LabMapper;
import com.medicare.repository.LabTestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The laboratory test catalog, maintained by administrators. Tests are deactivated, never deleted, because
 * orders reference them; orders keep their own copy of name, price, unit and reference range.
 */
@Service
public class LabCatalogService {

    private final LabTestRepository repository;
    private final AuditService auditService;

    public LabCatalogService(LabTestRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<LabTestResponse> list(String search, boolean activeOnly) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return repository.search(term, activeOnly).stream().map(LabMapper::toTest).toList();
    }

    @Transactional
    public LabTestResponse create(LabTestRequest req) {
        String code = req.code().trim().toUpperCase();
        String name = req.name().trim();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("LAB_TEST_CODE_EXISTS", "A test with this code already exists");
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("LAB_TEST_NAME_EXISTS", "A test with this name already exists");
        }
        LabTest t = new LabTest(code);
        apply(t, code, name, req);
        repository.saveAndFlush(t);
        auditService.record("LAB_TEST_CREATED", "LabTest", t.getId(), AuditOutcome.SUCCESS, code);
        return LabMapper.toTest(t);
    }

    @Transactional
    public LabTestResponse update(Long id, LabTestRequest req) {
        LabTest t = find(id);
        String code = req.code().trim().toUpperCase();
        String name = req.name().trim();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("LAB_TEST_CODE_EXISTS", "A test with this code already exists");
        }
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("LAB_TEST_NAME_EXISTS", "A test with this name already exists");
        }
        apply(t, code, name, req);
        repository.saveAndFlush(t);
        auditService.record("LAB_TEST_UPDATED", "LabTest", id, AuditOutcome.SUCCESS, code);
        return LabMapper.toTest(t);
    }

    @Transactional
    public LabTestResponse setActive(Long id, boolean active) {
        LabTest t = find(id);
        t.setActive(active);
        repository.saveAndFlush(t);
        auditService.record(active ? "LAB_TEST_ACTIVATED" : "LAB_TEST_DEACTIVATED", "LabTest", id, AuditOutcome.SUCCESS, t.getCode());
        return LabMapper.toTest(t);
    }

    private static void apply(LabTest t, String code, String name, LabTestRequest req) {
        t.update(code, name, blankToNull(req.description()), blankToNull(req.sampleType()), blankToNull(req.unit()),
                blankToNull(req.referenceRange()), req.price(), req.turnaroundHours());
    }

    private LabTest find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lab test", id));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
