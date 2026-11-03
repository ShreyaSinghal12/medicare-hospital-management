package com.medicare.dto.clinical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Amendment of a finalized consultation: the new values plus a mandatory reason. */
public record AmendConsultationRequest(
        @NotNull @Valid ConsultationContent content,
        @NotBlank @Size(min = 5, max = 500) String reason) {
}
