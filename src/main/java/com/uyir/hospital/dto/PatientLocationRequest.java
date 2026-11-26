package com.uyir.hospital.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body for PUT /emergency-bookings/{id}/patient-location: the patient's phone position right now.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientLocationRequest {
    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double lat;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double lng;

    @PositiveOrZero
    private Double accuracy;

    @DecimalMin("0")
    @DecimalMax("360")
    private Double heading;

    @PositiveOrZero
    private Double speed;

    // When the phone took the fix; optional (queued background fixes arrive late).
    private Instant recordedAt;
}
