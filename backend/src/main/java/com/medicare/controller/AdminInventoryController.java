package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.inventory.AdjustmentRequest;
import com.medicare.dto.inventory.DispenseRequest;
import com.medicare.dto.inventory.DispensingPrescriptionView;
import com.medicare.dto.inventory.DispensingResponse;
import com.medicare.dto.inventory.DispensingSummary;
import com.medicare.dto.inventory.InventoryAlerts;
import com.medicare.dto.inventory.MedicineDetail;
import com.medicare.dto.inventory.MedicineRequest;
import com.medicare.dto.inventory.MedicineStatusRequest;
import com.medicare.dto.inventory.MedicineSummary;
import com.medicare.dto.inventory.StockInRequest;
import com.medicare.dto.inventory.TransactionResponse;
import com.medicare.enums.InventoryTxnType;
import com.medicare.enums.MedicineCategory;
import com.medicare.security.CurrentUser;
import com.medicare.service.DispensingService;
import com.medicare.service.InventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Medicine inventory and dispensing (administrators only). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: inventory and dispensing")
public class AdminInventoryController {

    private final InventoryService inventoryService;
    private final DispensingService dispensingService;

    public AdminInventoryController(InventoryService inventoryService, DispensingService dispensingService) {
        this.inventoryService = inventoryService;
        this.dispensingService = dispensingService;
    }

    @GetMapping("/api/admin/inventory/medicines")
    @Operation(summary = "Medicines with usable stock, expired stock, nearest expiry and low-stock flag")
    public PageResponse<MedicineSummary> medicines(@RequestParam(required = false) String search,
                                                   @RequestParam(required = false) MedicineCategory category,
                                                   @RequestParam(defaultValue = "false") boolean includeInactive,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return inventoryService.search(search, category, includeInactive, page, size);
    }

    @GetMapping("/api/admin/inventory/medicines/{id}")
    public MedicineDetail medicine(@PathVariable Long id) {
        return inventoryService.get(id);
    }

    @PostMapping("/api/admin/inventory/medicines")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicineDetail create(@Valid @RequestBody MedicineRequest request) {
        return inventoryService.create(request);
    }

    @PutMapping("/api/admin/inventory/medicines/{id}")
    public MedicineDetail update(@PathVariable Long id, @Valid @RequestBody MedicineRequest request) {
        return inventoryService.update(id, request);
    }

    @PatchMapping("/api/admin/inventory/medicines/{id}/status")
    public MedicineDetail setStatus(@PathVariable Long id, @Valid @RequestBody MedicineStatusRequest request) {
        return inventoryService.setActive(id, request.active());
    }

    @PostMapping("/api/admin/inventory/medicines/{id}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Receive stock as a new batch (expiry must be in the future)")
    public MedicineDetail stockIn(@PathVariable Long id, @Valid @RequestBody StockInRequest request) {
        return inventoryService.stockIn(CurrentUser.get(), id, request);
    }

    @PostMapping("/api/admin/inventory/batches/{batchId}/adjustments")
    @Operation(summary = "Adjust a batch (ADJUSTMENT_IN, ADJUSTMENT_OUT, STOCK_OUT) with a reason; never below zero")
    public MedicineDetail adjust(@PathVariable Long batchId, @Valid @RequestBody AdjustmentRequest request) {
        return inventoryService.adjust(CurrentUser.get(), batchId, request);
    }

    @PostMapping("/api/admin/inventory/batches/{batchId}/write-off")
    @Operation(summary = "Write off the remaining stock of an expired batch")
    public MedicineDetail writeOff(@PathVariable Long batchId) {
        return inventoryService.writeOffExpired(CurrentUser.get(), batchId);
    }

    @GetMapping("/api/admin/inventory/transactions")
    @Operation(summary = "Stock ledger (filter by medicine and type)")
    public PageResponse<TransactionResponse> transactions(@RequestParam(required = false) Long medicineId,
                                                          @RequestParam(required = false) InventoryTxnType type,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return inventoryService.transactions(medicineId, type, page, size);
    }

    @GetMapping("/api/admin/inventory/alerts")
    @Operation(summary = "Low stock, batches expiring within the warning window, expired batches with stock")
    public InventoryAlerts alerts() {
        return inventoryService.alerts();
    }

    @GetMapping("/api/admin/dispensing/prescriptions/{prescriptionNo}")
    @Operation(summary = "Look up a finalized prescription by number for dispensing (medicine lines only; audited)")
    public DispensingPrescriptionView lookup(@PathVariable String prescriptionNo) {
        return dispensingService.lookup(prescriptionNo);
    }

    @PostMapping("/api/admin/dispensing")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record medicines handed out against a finalized prescription")
    public DispensingResponse dispense(@Valid @RequestBody DispenseRequest request) {
        return dispensingService.dispense(CurrentUser.get(), request);
    }

    @GetMapping("/api/admin/dispensing")
    public PageResponse<DispensingSummary> dispensings(@RequestParam(required = false) String search,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return dispensingService.list(search, page, size);
    }

    @GetMapping("/api/admin/dispensing/{id}")
    public DispensingResponse dispensing(@PathVariable Long id) {
        return dispensingService.get(id);
    }
}
