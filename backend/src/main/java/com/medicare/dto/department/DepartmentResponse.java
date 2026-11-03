package com.medicare.dto.department;

/** Department with the number of active doctors assigned to it. */
public record DepartmentResponse(
        Long id,
        String name,
        String description,
        boolean active,
        long activeDoctorCount) {
}
