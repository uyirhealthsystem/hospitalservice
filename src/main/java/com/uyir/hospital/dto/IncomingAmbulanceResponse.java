package com.uyir.hospital.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GET /emergency-bookings/{id}/ambulance (hospital): where the ambulance bringing this emergency patient is.
// status is the ambulance ride's status: SEARCHING -> EN_ROUTE_TO_PICKUP -> ARRIVED -> PICKED_UP (coming here)
// -> PAYMENT_PENDING / COMPLETED (dropped), or CANCELLED / NO_AMBULANCE_FOUND.
// etaMinutes / distanceKm are to the ambulance's current target: the patient before PICKED_UP, this hospital after.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomingAmbulanceResponse {

    private String emergencyBookingId;
    private String ambulanceBookingId;
    private String patientName;
    private String patientPhone;
    private String emergencyType;

    private String status;
    private boolean patientOnBoard;
    private String ambulanceType;

    private Place pickup;
    private Place destination;

    private Driver driver;
    private Location location;
    private Long etaMinutes;
    private Double distanceKm;

    private Instant bookedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Place {
        private double lat;
        private double lng;
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Driver {
        private String name;
        private String phone;
        private String plateNumber;
        private String ambulanceType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private double lat;
        private double lng;
        private Double heading;
        private Instant updatedAt;
    }
}
