package com.medicare.dto;

import java.util.List;

/** Public, non-sensitive hospital information shown on the landing page. */
public record HospitalInfoResponse(
        String hospitalName,
        String address,
        String phone,
        String email,
        String timeZone,
        String cancellationPolicy,
        List<DepartmentSummary> departments) {

    public record DepartmentSummary(Long id, String name, String description) {
    }
}
