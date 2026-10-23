package com.uyir.hospital.event;

import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import java.time.Instant;

// `data` of the EmergencyBookingRequested (v1) event. Carries the hospital's name and email so
// notification-service can send the alert without calling back into this service.
public record EmergencyBookingRequestedEvent(
        String bookingId,
        String hospitalId,
        String hospitalName,
        String hospitalEmail,
        String patientName,
        String patientPhone,
        String emergencyType,
        Instant requestedAt) {

    public static final String EVENT_TYPE = "EmergencyBookingRequested";
    public static final int EVENT_VERSION = 1;

    public static EmergencyBookingRequestedEvent of(EmergencyBooking booking, Hospital hospital) {
        String email = hospital.getContactDetails() == null ? null : hospital.getContactDetails().getEmail();
        return new EmergencyBookingRequestedEvent(
                booking.getId(),
                booking.getHospitalId(),
                hospital.getHospitalName(),
                email,
                booking.getPatientName(),
                booking.getPatientPhone(),
                booking.getEmergencyType(),
                booking.getRequestedAt());
    }
}
