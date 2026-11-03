package com.medicare.dto.clinical;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * Clinical fields of a consultation, all typed by the doctor. {@code version} is the value
 * the client last loaded; a stale version is rejected with 409 so concurrent edits never
 * silently overwrite each other.
 */
public record ConsultationContent(
        @Size(max = 5000) String symptoms,
        @Size(max = 5000) String observations,
        @Size(max = 5000) String diagnosis,
        @Size(max = 5000) String treatmentPlan,
        @Size(max = 5000) String notes,
        LocalDate followUpDate,
        @Size(max = 500) String followUpNotes,
        Long version) {
}
