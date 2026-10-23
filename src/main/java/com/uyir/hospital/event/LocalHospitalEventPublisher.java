package com.uyir.hospital.event;

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

    public LocalHospitalEventPublisher(EmergencyAlertHub alertHub, Environment environment) {
        this.alertHub = alertHub;
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
}
