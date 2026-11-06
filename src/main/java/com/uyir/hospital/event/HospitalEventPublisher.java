package com.uyir.hospital.event;

// Publishes hospital.events. Best-effort: implementations never throw, so a Kafka outage can't fail
// the booking or the job that triggered the event.
public interface HospitalEventPublisher {

    // Alerts a hospital about a new emergency booking.
    void emergencyBookingRequested(EmergencyBookingRequestedEvent event);

    // Any change to an emergency booking or appointment; every pod sends it to the hospital's and the
    // patient's live WebSocket connections.
    void bookingUpdated(BookingUpdatedEvent event);

    // Asks notification-service to remind a patient to book the follow-up the hospital asked for.
    void followUpReminderDue(FollowUpReminderDueEvent event);
}
