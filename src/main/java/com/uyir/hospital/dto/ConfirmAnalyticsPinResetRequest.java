package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/v1/hospital/analytics-pin/reset/confirm - the code from the reset email plus the new PIN.
public record ConfirmAnalyticsPinResetRequest(
        @NotBlank(message = "resetCode is required") String resetCode,
        @NotBlank(message = "newPin is required") String newPin) {
}
