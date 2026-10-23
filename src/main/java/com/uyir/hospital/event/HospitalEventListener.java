package com.uyir.hospital.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Every pod reads every hospital.events message: the consumer group id is unique per pod (random.uuid),
// so Kafka broadcasts instead of load-balancing - whichever pod a hospital's dashboard is connected to
// gets the alert. Starts at "latest": a pod that was down has no open dashboards to catch up.
@Component
@Conditional(KafkaEventsCondition.Enabled.class)
public class HospitalEventListener {

    private static final Logger log = LoggerFactory.getLogger(HospitalEventListener.class);

    private final EmergencyAlertHub alertHub;
    private final JsonMapper jsonMapper;

    public HospitalEventListener(EmergencyAlertHub alertHub, JsonMapper jsonMapper) {
        this.alertHub = alertHub;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${hospital.events.topic}",
            groupId = "hospitalservice-live-${random.uuid}",
            properties = "auto.offset.reset=latest")
    public void onMessage(String message) {
        try {
            JsonNode envelope = jsonMapper.readTree(message);
            if (!EmergencyBookingRequestedEvent.EVENT_TYPE.equals(envelope.path("eventType").asString())) {
                return;
            }
            alertHub.push(jsonMapper.treeToValue(envelope.get("data"), EmergencyBookingRequestedEvent.class));
        } catch (RuntimeException ex) {
            // A bad message is not retryable - skip it rather than block the partition.
            log.warn("Skipping unreadable hospital.events message: {}", ex.getMessage());
        }
    }
}
