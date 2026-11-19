package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/v1/hospital/claim - a HOSPITAL account links itself to the hospital a PRO onboarded, using the
// hospital id and the one-time claim code from the email. The code may be typed with or without the dash
// and in any case ("k7p4-qx2m", "K7P4QX2M").
public record ClaimHospitalRequest(
        @NotBlank(message = "hospitalId is required") String hospitalId,
        @NotBlank(message = "claimCode is required") String claimCode) {
}
