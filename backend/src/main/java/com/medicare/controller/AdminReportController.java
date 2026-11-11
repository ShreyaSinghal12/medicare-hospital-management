package com.medicare.controller;

import java.time.LocalDate;

import com.medicare.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operational reports: preview, CSV and PDF (administrators). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/admin/reports/{type}")
    @Operation(summary = "Preview (first 100 rows). Types: APPOINTMENTS, PAYMENTS, INVOICES, LAB_WORKLOAD, INVENTORY, AUDIT")
    public ReportService.Report preview(@PathVariable ReportService.Type type,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.preview(type, from, to);
    }

    @GetMapping("/api/admin/reports/{type}/export")
    @Operation(summary = "Download as CSV or PDF (at most 10,000 rows; audited)")
    public ResponseEntity<byte[]> export(@PathVariable ReportService.Type type, @RequestParam ReportService.Format format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportService.FileResult f = reportService.export(type, format, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(f.content());
    }
}
