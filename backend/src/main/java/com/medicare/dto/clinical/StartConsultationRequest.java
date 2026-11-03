package com.medicare.dto.clinical;

import jakarta.validation.constraints.NotNull;

public record StartConsultationRequest(@NotNull Long appointmentId) {
}
