package com.uyir.hospital.event;

// What happened to a booking - the `type` of a BookingUpdated event and of the live WebSocket message.
public enum BookingUpdateType {
    EMERGENCY_REQUESTED,
    EMERGENCY_ACCEPTED,
    EMERGENCY_REJECTED,
    EMERGENCY_COMPLETED,
    EMERGENCY_CANCELLED,
    APPOINTMENT_BOOKED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_COMPLETED,
    APPOINTMENT_NO_SHOW
}
