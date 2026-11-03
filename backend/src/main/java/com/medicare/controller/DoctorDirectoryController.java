package com.medicare.controller;

import java.util.List;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.department.DepartmentResponse;
import com.medicare.dto.doctor.DoctorDetailResponse;
import com.medicare.dto.doctor.DoctorSummaryResponse;
import com.medicare.service.DepartmentService;
import com.medicare.service.DoctorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public (no login) doctor directory and department list. Only active records are shown. */
@RestController
@Tag(name = "Doctor directory", description = "Public doctor search and profiles")
public class DoctorDirectoryController {

    private final DoctorService doctorService;
    private final DepartmentService departmentService;

    public DoctorDirectoryController(DoctorService doctorService, DepartmentService departmentService) {
        this.doctorService = doctorService;
        this.departmentService = departmentService;
    }

    @GetMapping("/api/doctors")
    @Operation(summary = "Search active doctors by name/specialty, department and specialization")
    public PageResponse<DoctorSummaryResponse> search(@RequestParam(required = false) String search,
                                                      @RequestParam(required = false) Long departmentId,
                                                      @RequestParam(required = false) String specialization,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "12") int size) {
        return doctorService.searchPublic(search, departmentId, specialization, page, size);
    }

    @GetMapping("/api/doctors/specializations")
    @Operation(summary = "Distinct specializations of active doctors (for filters)")
    public List<String> specializations() {
        return doctorService.specializations();
    }

    @GetMapping("/api/doctors/{id}")
    @Operation(summary = "Public doctor profile")
    public DoctorDetailResponse get(@PathVariable Long id) {
        return doctorService.getPublic(id);
    }

    @GetMapping("/api/public/departments")
    @Operation(summary = "Active departments with their number of active doctors")
    public List<DepartmentResponse> departments() {
        return departmentService.listActive();
    }
}
