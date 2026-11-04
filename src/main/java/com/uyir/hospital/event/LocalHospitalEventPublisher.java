package com.uyir.hospital.event;

import com.uyir.hospital.live.LiveUpdateHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// Used when Kafka is off - hospital.events.provider=local, or KAFKA_BROKERS not set (a plain local run):
// alerts go straight to this pod's dashboards. No Kafka, so no email, and with more than one pod a dashboard
// on another pod never hears about the booking. Fine for local dev, never for production.
@Component
@Conditional(KafkaEventsCondition.Disabled.class)
public class LocalHospitalEventPublisher implements HospitalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalHospitalEventPublisher.class);

    private final EmergencyAlertHub alertHub;

    private final LiveUpdateHub liveUpdateHub;

    public LocalHospitalEventPublisher(EmergencyAlertHub alertHub, LiveUpdateHub liveUpdateHub, Environment environment) {
        this.alertHub = alertHub;
        this.liveUpdateHub = liveUpdateHub;
        String reason = "local".equalsIgnoreCase(environment.getProperty("hospital.events.provider", "").trim())
                ? "HOSPITAL_EVENTS_PROVIDER=local"
                : "KAFKA_BROKERS is not set";
        log.warn("Kafka is off ({}): emergency alerts reach this instance's dashboards only and no alert emails are sent. "
                + "Fine for local dev; set KAFKA_BROKERS in production.", reason);
    }

    @Override
    public void emergencyBookingRequested(EmergencyBookingRequestedEvent event) {
        alertHub.push(event);
    }

    @Override
    public void bookingUpdated(BookingUpdatedEvent event) {
        liveUpdateHub.push(event);
    }

    // No Kafka, so no notification-service: the reminder email is not sent.
    @Override
    public void followUpReminderDue(FollowUpReminderDueEvent event) {
        log.info("Kafka is off: follow-up reminder ({}) for appointment '{}' not emailed",
                event.reminderType(), event.appointmentId());
    }
}
