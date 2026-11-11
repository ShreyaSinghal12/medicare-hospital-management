package com.medicare.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import com.medicare.dto.billing.BillableCharges;
import com.medicare.dto.billing.BillingSummary;
import com.medicare.dto.billing.CancelInvoiceRequest;
import com.medicare.dto.billing.CreateInvoiceRequest;
import com.medicare.dto.billing.InvoiceResponse;
import com.medicare.dto.billing.InvoiceSummary;
import com.medicare.dto.billing.PaymentRequest;
import com.medicare.dto.common.PageResponse;
import com.medicare.enums.InvoiceStatus;
import com.medicare.security.CurrentUser;
import com.medicare.service.BillingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Billing: receptionists create invoices and record money; administrators read; patients read their own. */
@RestController
@Tag(name = "Billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/api/billing/patients/{patientId}/billable")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "Services the patient received and has not been billed for (prices from the server)")
    public BillableCharges billable(@PathVariable Long patientId) {
        return billingService.billable(patientId);
    }

    @PostMapping("/api/billing/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
        return billingService.create(CurrentUser.get(), request);
    }

    @GetMapping("/api/billing/invoices")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @Operation(summary = "Search invoices (number, patient name or ID), status, patient and issue dates")
    public PageResponse<InvoiceSummary> search(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) InvoiceStatus status,
                                               @RequestParam(required = false) Long patientId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return billingService.search(search, status, patientId, from, to, page, size);
    }

    @GetMapping("/api/billing/invoices/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN','PATIENT')")
    public InvoiceResponse get(@PathVariable Long id) {
        return billingService.get(CurrentUser.get(), id);
    }

    @GetMapping("/api/billing/invoices/{id}/pdf")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN','PATIENT')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        BillingService.Pdf pdf = billingService.pdf(CurrentUser.get(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }

    @PostMapping("/api/billing/invoices/{id}/payments")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "Record a payment (never more than the balance; no card numbers)")
    public InvoiceResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return billingService.pay(CurrentUser.get(), id, request);
    }

    @PostMapping("/api/billing/invoices/{id}/refunds")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "Record a refund (reason in notes; never more than paid)")
    public InvoiceResponse refund(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return billingService.refund(CurrentUser.get(), id, request);
    }

    @PostMapping("/api/billing/invoices/{id}/cancel")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Operation(summary = "Cancel an unpaid invoice; its charges can be billed again")
    public InvoiceResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelInvoiceRequest request) {
        return billingService.cancel(CurrentUser.get(), id, request.reason());
    }

    @GetMapping("/api/patients/me/invoices")
    @PreAuthorize("hasRole('PATIENT')")
    public PageResponse<InvoiceSummary> mine(@RequestParam(required = false) InvoiceStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return billingService.mine(CurrentUser.get(), status, page, size);
    }

    @GetMapping("/api/admin/billing/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Invoiced, collected, refunded and outstanding amounts (default: this month)")
    public BillingSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return billingService.summary(from, to);
    }
}
