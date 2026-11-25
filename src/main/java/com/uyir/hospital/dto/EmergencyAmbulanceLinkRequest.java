package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body for PATCH /emergency-bookings/{id}/ambulance: the patient's ambulance-service ride to this hospital.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAmbulanceLinkRequest {

    @NotBlank
    @Size(max = 64)
    private String ambulanceBookingId;
}
