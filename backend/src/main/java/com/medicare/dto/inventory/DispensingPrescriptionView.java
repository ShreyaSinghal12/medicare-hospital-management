package com.medicare.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What an administrator needs to dispense: who, which medicines and how many. Diagnosis, notes, dosage
 * instructions and the patient's history are deliberately not included.
 */
public record DispensingPrescriptionView(
        Long prescriptionId,
        String prescriptionNo,
        int versionNumber,
        Instant finalizedAt,
        String patientName,
        String patientCode,
        String doctorName,
        List<Line> items,
        List<DispensingSummary> previousDispensings) {

    /** {@code remainingQuantity} is null when the doctor did not state a quantity (such a line is dispensed once). */
    public record Line(Long prescriptionItemId, String medicineName, String strength, Integer prescribedQuantity,
                       int dispensedQuantity, Integer remainingQuantity, boolean fullyDispensed, List<Candidate> candidates) {
    }

    /** A catalog medicine whose name and strength match the prescribed line, with its usable batches (earliest expiry first). */
    public record Candidate(Long medicineId, String name, String strength, String unit, BigDecimal unitPrice,
                            List<CandidateBatch> batches) {
    }

    public record CandidateBatch(Long batchId, String batchNumber, LocalDate expiryDate, int available) {
    }
}
