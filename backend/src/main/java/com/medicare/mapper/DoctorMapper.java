package com.medicare.mapper;

import com.medicare.dto.doctor.AdminDoctorResponse;
import com.medicare.dto.doctor.DoctorDetailResponse;
import com.medicare.dto.doctor.DoctorSummaryResponse;
import com.medicare.entity.Doctor;
import com.medicare.entity.User;

public final class DoctorMapper {

    private DoctorMapper() {
    }

    public static DoctorSummaryResponse toSummary(Doctor d) {
        return new DoctorSummaryResponse(d.getId(), d.getUser().getFullName(), d.getDepartment().getId(),
                d.getDepartment().getName(), d.getSpecialization(), d.getQualifications(), d.getExperienceYears(),
                d.getConsultationFee(), d.getSlotDurationMinutes());
    }

    public static DoctorDetailResponse toDetail(Doctor d) {
        return new DoctorDetailResponse(d.getId(), d.getUser().getFullName(), d.getDepartment().getId(),
                d.getDepartment().getName(), d.getSpecialization(), d.getQualifications(), d.getExperienceYears(),
                d.getConsultationFee(), d.getSlotDurationMinutes(), d.getBio());
    }

    public static AdminDoctorResponse toAdmin(Doctor d) {
        User u = d.getUser();
        return new AdminDoctorResponse(d.getId(), u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isActive(),
                d.getDepartment().getId(), d.getDepartment().getName(), d.getSpecialization(), d.getQualifications(),
                d.getLicenseNumber(), d.getExperienceYears(), d.getConsultationFee(), d.getSlotDurationMinutes(),
                d.getBio(), u.getLastLoginAt(), d.getCreatedAt());
    }
}
