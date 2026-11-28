package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/v1/hospital/analytics-pin/change - a wrong currentPin counts toward the lock, like analytics.
public record ChangeAnalyticsPinRequest(
        @NotBlank(message = "currentPin is required") String currentPin,
        @NotBlank(message = "newPin is required") String newPin) {
}
