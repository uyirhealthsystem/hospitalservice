package com.uyir.hospital.ambulance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

// The ambulance service's GET /api/v1/ambulance/internal/bookings/{id}/track: one ride, its driver, the
// ambulance's last GPS position and the ETA to its current target (the pickup, then the drop = the hospital).
@JsonIgnoreProperties(ignoreUnknown = true)
public record AmbulanceTrack(String patientId, Booking booking, Driver driver, Location location, Eta eta) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Booking(
            String id,
            String type,
            String status,
            String emergencyType,
            String ambulanceType,
            Place pickup,
            Place drop,
            BigDecimal fare,
            Instant paidAt,
            Instant createdAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(double lat, double lng, String address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Driver(String name, String phone, String plateNumber, String ambulanceType, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(double lat, double lng, Double heading, Instant at) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Eta(long minutes, double distanceKm) {
    }
}
