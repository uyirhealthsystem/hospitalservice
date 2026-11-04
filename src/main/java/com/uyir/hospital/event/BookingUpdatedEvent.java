package com.uyir.hospital.event;

import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.security.Role;
import java.time.Instant;

// `data` of the BookingUpdated (v1) event: any change to an emergency booking or a doctor appointment.
// Every pod turns it into a live WebSocket message for the hospital and the patient (LiveUpdateHub); push
// notifications will use the same event. Only fields both sides may see - no reasons, symptoms or fees.
public record BookingUpdatedEvent(
        BookingUpdateType type,
        BookingKind bookingType,
        String bookingId,
        String hospitalId,
        String patientId,
        String patientName,
        // Appointments only.
        String doctorId,
        Instant appointmentDateTime,
        Integer tokenNumber,
        // Emergencies only.
        String emergencyType,
        String status,
        Role changedBy,
        Instant occurredAt) {

    public static final String EVENT_TYPE = "BookingUpdated";
    public static final int EVENT_VERSION = 1;

    public enum BookingKind {
        EMERGENCY,
        APPOINTMENT
    }

    public static BookingUpdatedEvent of(BookingUpdateType type, EmergencyBooking booking, Role changedBy) {
        return new BookingUpdatedEvent(type, BookingKind.EMERGENCY, booking.getId(), booking.getHospitalId(),
                booking.getPatientId(), booking.getPatientName(), null, null, null, booking.getEmergencyType(),
                booking.getStatus() == null ? null : booking.getStatus().name(), changedBy, Instant.now());
    }

    public static BookingUpdatedEvent of(BookingUpdateType type, DoctorAppointmentBooking booking, Role changedBy) {
        return new BookingUpdatedEvent(type, BookingKind.APPOINTMENT, booking.getId(), booking.getHospitalId(),
                booking.getPatientId(), booking.getPatientName(), booking.getDoctorId(), booking.getAppointmentDateTime(),
                booking.getTokenNumber(), null, booking.getStatus() == null ? null : booking.getStatus().name(),
                changedBy, Instant.now());
    }
}
