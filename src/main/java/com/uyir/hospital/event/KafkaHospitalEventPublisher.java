package com.uyir.hospital.event;

import com.uyir.hospital.live.LiveUpdateHub;
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
    private final LiveUpdateHub liveUpdateHub;
    private final String topic;
    // KafkaTemplate.send blocks the caller on broker metadata (up to max.block.ms) while Kafka is down -
    // the patient's booking request must not wait on that.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public KafkaHospitalEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            EmergencyAlertHub alertHub,
            LiveUpdateHub liveUpdateHub,
            HospitalEventProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.alertHub = alertHub;
        this.liveUpdateHub = liveUpdateHub;
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

    // Keyed by hospital id, so one hospital's updates stay in order. If Kafka is unreachable, only this pod's
    // live connections get it; the change is saved either way and shows on the client's next refresh.
    @Override
    public void bookingUpdated(BookingUpdatedEvent event) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(
                    HospitalEvent.of(BookingUpdatedEvent.EVENT_TYPE, BookingUpdatedEvent.EVENT_VERSION, event));
        } catch (RuntimeException ex) {
            log.error("Could not serialize BookingUpdated ({}) for booking '{}'", event.type(), event.bookingId(), ex);
            pushLocally(event);
            return;
        }
        executor.execute(() -> {
            try {
                kafkaTemplate.send(topic, event.hospitalId(), json).whenComplete((result, ex) -> {
                    if (ex != null) {
                        onBookingUpdateFailed(event, ex);
                    }
                });
            } catch (RuntimeException ex) {
                onBookingUpdateFailed(event, ex);
            }
        });
    }

    private void onBookingUpdateFailed(BookingUpdatedEvent event, Throwable ex) {
        log.error("Failed to publish BookingUpdated ({}) for booking '{}' to '{}'; updating this pod's live connections only",
                event.type(), event.bookingId(), topic, ex);
        pushLocally(event);
    }

    private void pushLocally(BookingUpdatedEvent event) {
        try {
            liveUpdateHub.push(event);
        } catch (RuntimeException ex) {
            log.warn("Live update for booking '{}' failed: {}", event.bookingId(), ex.getMessage());
        }
    }

    // Keyed by patient id. A lost reminder is logged, not retried: it is a courtesy, and the patient still
    // sees the follow-up in the app.
    @Override
    public void followUpReminderDue(FollowUpReminderDueEvent event) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(HospitalEvent.of(
                    FollowUpReminderDueEvent.EVENT_TYPE, FollowUpReminderDueEvent.EVENT_VERSION, event));
        } catch (RuntimeException ex) {
            log.error("Could not serialize FollowUpReminderDue for appointment '{}'", event.appointmentId(), ex);
            return;
        }
        executor.execute(() -> {
            try {
                kafkaTemplate.send(topic, event.patientId(), json).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish FollowUpReminderDue for appointment '{}'", event.appointmentId(), ex);
                    }
                });
            } catch (RuntimeException ex) {
                log.error("Failed to publish FollowUpReminderDue for appointment '{}'", event.appointmentId(), ex);
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
