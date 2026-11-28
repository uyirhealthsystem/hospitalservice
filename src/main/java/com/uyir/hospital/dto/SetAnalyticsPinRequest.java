package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/v1/hospital/analytics-pin - the hospital sets its first analytics PIN (4-6 digits).
public record SetAnalyticsPinRequest(@NotBlank(message = "pin is required") String pin) {
}
