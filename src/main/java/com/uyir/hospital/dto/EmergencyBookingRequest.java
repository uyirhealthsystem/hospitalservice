package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyBookingRequest {

    @NotBlank
    private String hospitalId;

    @NotBlank
    private String emergencyType;

    @NotBlank
    private String patientName;

    @NotBlank
    private String patientPhone;
}
