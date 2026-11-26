package com.uyir.hospital.model.embedded;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// The latest position the patient's phone reported while on the way to the hospital.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientLocation {
    private double lat;
    private double lng;
    // Metres, as reported by the phone (null when unknown).
    private Double accuracy;
    // Degrees from north, 0-360 (null when unknown or standing still).
    private Double heading;
    // Metres per second (null when unknown).
    private Double speed;
    // When the phone took the fix (falls back to when the server received it).
    private Instant recordedAt;
    // When the server received it.
    private Instant updatedAt;
}
