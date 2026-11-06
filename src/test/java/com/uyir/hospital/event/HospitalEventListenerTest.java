package com.uyir.hospital.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.uyir.hospital.live.LiveUpdateHub;
import com.uyir.hospital.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class HospitalEventListenerTest {

    private final EmergencyAlertHub alertHub = mock(EmergencyAlertHub.class);
    private final LiveUpdateHub liveUpdateHub = mock(LiveUpdateHub.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HospitalEventListener listener = new HospitalEventListener(alertHub, liveUpdateHub, jsonMapper);

    @Test
    void emergencyBookingRequested_isPushedToDashboards() {
        EmergencyBookingRequestedEvent data = new EmergencyBookingRequestedEvent("b1", "h1", "City Hospital",
                "er@city.test", "John Doe", "9999999999", "Cardiac Arrest", Instant.parse("2026-10-19T10:00:00Z"));
        // Round-trips through the same serialization KafkaHospitalEventPublisher uses.
        String message = jsonMapper.writeValueAsString(HospitalEvent.of(
                EmergencyBookingRequestedEvent.EVENT_TYPE, EmergencyBookingRequestedEvent.EVENT_VERSION, data));

        listener.onMessage(message);

        ArgumentCaptor<EmergencyBookingRequestedEvent> captor = ArgumentCaptor.forClass(EmergencyBookingRequestedEvent.class);
        verify(alertHub).push(captor.capture());
        assertThat(captor.getValue()).isEqualTo(data);
    }

    @Test
    void envelope_carriesTheContractNotificationServiceValidates() {
        String message = jsonMapper.writeValueAsString(HospitalEvent.of("EmergencyBookingRequested", 1,
                new EmergencyBookingRequestedEvent("b1", "h1", null, null, "John", "99", "Burns",
                        Instant.parse("2026-10-19T10:00:00Z"))));

        // notification-service's zod schema expects ISO-8601 strings, not epoch numbers.
        assertThat(message).contains("\"eventType\":\"EmergencyBookingRequested\"");
        assertThat(message).contains("\"eventVersion\":1");
        assertThat(message).contains("\"requestedAt\":\"2026-10-19T10:00:00Z\"");
        assertThat(message).containsPattern("\"occurredAt\":\"\\d{4}-\\d{2}-\\d{2}T");
    }

    @Test
    void bookingUpdated_isPushedToLiveConnectionsOnly() {
        BookingUpdatedEvent data = new BookingUpdatedEvent(BookingUpdateType.APPOINTMENT_CANCELLED,
                BookingUpdatedEvent.BookingKind.APPOINTMENT, "a1", "h1", "p1", "John", "d1",
                Instant.parse("2026-11-05T05:30:00Z"), 7, null, "CANCELLED", Role.PATIENT,
                Instant.parse("2026-11-04T10:12:00Z"));
        String message = jsonMapper.writeValueAsString(HospitalEvent.of(
                BookingUpdatedEvent.EVENT_TYPE, BookingUpdatedEvent.EVENT_VERSION, data));

        listener.onMessage(message);

        verify(liveUpdateHub).push(data);
        verify(alertHub, never()).push(any());
    }

    @Test
    void otherEventTypes_areIgnored() {
        listener.onMessage("{\"eventType\":\"SomethingElse\",\"eventVersion\":1,\"occurredAt\":\"x\",\"data\":{}}");

        verify(alertHub, never()).push(any());
        verify(liveUpdateHub, never()).push(any());
    }

    @Test
    void malformedMessage_isSkippedWithoutThrowing() {
        listener.onMessage("not json");

        verify(alertHub, never()).push(any());
    }
}
