package com.medicare.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
}
