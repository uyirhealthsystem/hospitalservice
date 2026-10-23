package com.uyir.hospital.event;

// Alerts a hospital about a new emergency booking. Best-effort: implementations never throw, so a
// Kafka outage can't fail the booking itself.
public interface HospitalEventPublisher {

    void emergencyBookingRequested(EmergencyBookingRequestedEvent event);
}
