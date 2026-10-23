package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body for rejecting (hospital) or cancelling (patient) an emergency booking.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyBookingReasonRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
