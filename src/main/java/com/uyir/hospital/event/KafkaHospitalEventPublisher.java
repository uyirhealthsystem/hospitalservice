package com.uyir.hospital.event;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Publishes to hospital.events, keyed by hospital id. notification-service turns the event into an
// email; every pod's HospitalEventListener turns it into a live dashboard alert.
@Component
@Conditional(KafkaEventsCondition.Enabled.class)
public class KafkaHospitalEventPublisher implements HospitalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaHospitalEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final EmergencyAlertHub alertHub;
    private final String topic;
    // KafkaTemplate.send blocks the caller on broker metadata (up to max.block.ms) while Kafka is down -
    // the patient's booking request must not wait on that.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public KafkaHospitalEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            EmergencyAlertHub alertHub,
            HospitalEventProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.alertHub = alertHub;
        this.topic = properties.topic();
    }

    @Override
    public void emergencyBookingRequested(EmergencyBookingRequestedEvent event) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(HospitalEvent.of(
                    EmergencyBookingRequestedEvent.EVENT_TYPE, EmergencyBookingRequestedEvent.EVENT_VERSION, event));
        } catch (RuntimeException ex) {
            log.error("Could not serialize EmergencyBookingRequested for booking '{}'", event.bookingId(), ex);
            alertHub.push(event);
            return;
        }
        executor.execute(() -> {
            try {
                kafkaTemplate.send(topic, event.hospitalId(), json).whenComplete((result, ex) -> {
                    if (ex != null) {
                        onSendFailed(event, ex);
                    }
                });
            } catch (RuntimeException ex) {
                onSendFailed(event, ex);
            }
        });
    }

    // No email goes out and other pods' dashboards miss it, but dashboards connected to this pod still
    // get the alert - and the booking is saved either way, so it shows up on the dashboard's next refresh.
    private void onSendFailed(EmergencyBookingRequestedEvent event, Throwable ex) {
        log.error("Failed to publish EmergencyBookingRequested for booking '{}' to '{}'; alerting this pod's dashboards only",
                event.bookingId(), topic, ex);
        alertHub.push(event);
    }
}
