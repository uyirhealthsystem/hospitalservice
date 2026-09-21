package com.uyir.hospital.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAppointmentBookingRequest {

    @NotBlank
    private String hospitalId;

    @NotBlank
    private String doctorId;

    @NotNull
    @Future
    private Instant appointmentDateTime;

    private String reason;

    @NotBlank
    private String patientName;

    @NotBlank
    private String patientPhone;
}
