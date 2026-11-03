package com.medicare.controller;

import com.medicare.dto.HospitalInfoResponse;
import com.medicare.service.HospitalSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated, non-sensitive information for the public website. */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Information available without logging in")
public class PublicController {

    private final HospitalSettingsService settingsService;

    public PublicController(HospitalSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/hospital-info")
    @Operation(summary = "Hospital name, contact details and active departments")
    public HospitalInfoResponse hospitalInfo() {
        return settingsService.getPublicInfo();
    }
}
