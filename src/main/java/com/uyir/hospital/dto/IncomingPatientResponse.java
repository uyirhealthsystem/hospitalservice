package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.ArrivalMode;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GET /emergency-bookings/{id}/patient-location (hospital): where the emergency patient's phone is.
// location is only given while the booking is ACCEPTED (patient on the way); null before the first fix
// and after the emergency is completed / cancelled.
// distanceKm is a straight line from the patient to this hospital (null when either point is unknown).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomingPatientResponse {
    private String emergencyBookingId;
    private String patientName;
    private String patientPhone;
    private String emergencyType;
    private EmergencyBookingStatus status;
    private ArrivalMode arrivalMode;
    private Location location;
    private Point hospital;
    private Double distanceKm;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private double lat;
        private double lng;
        private Double accuracy;
        private Double heading;
        private Double speed;
        private Instant recordedAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private double lat;
        private double lng;
    }
}
