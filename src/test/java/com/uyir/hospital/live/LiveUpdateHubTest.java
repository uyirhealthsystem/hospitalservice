package com.uyir.hospital.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.event.BookingUpdateType;
import com.uyir.hospital.event.BookingUpdatedEvent;
import com.uyir.hospital.security.Role;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class LiveUpdateHubTest {

    private final LiveUpdateHub hub = new LiveUpdateHub(JsonMapper.builder().build(),
            new LiveProperties(List.of("*"), Duration.ofSeconds(10), DataSize.ofKilobytes(512), 2));

    private final BookingUpdatedEvent event = new BookingUpdatedEvent(BookingUpdateType.APPOINTMENT_BOOKED,
            BookingUpdatedEvent.BookingKind.APPOINTMENT, "a1", "h1", "p1", "John", "d1",
            Instant.parse("2026-11-05T05:30:00Z"), 3, null, "CONFIRMED", Role.PATIENT,
            Instant.parse("2026-11-04T10:00:00Z"));

    private static WebSocketSession session(String id, LiveSubject subject) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(LiveSubject.ATTRIBUTE, subject);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private static String sentText(WebSocketSession session) throws IOException {
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        return ((TextMessage) captor.getValue()).getPayload();
    }

    @Test
    void push_reachesTheHospitalAndThePatientOnly() throws IOException {
        WebSocketSession hospital = session("s1", LiveSubject.hospital("h1"));
        WebSocketSession patient = session("s2", LiveSubject.patient("p1"));
        WebSocketSession otherHospital = session("s3", LiveSubject.hospital("h2"));
        WebSocketSession otherPatient = session("s4", LiveSubject.patient("p2"));
        hub.register(LiveSubject.hospital("h1"), hospital);
        hub.register(LiveSubject.patient("p1"), patient);
        hub.register(LiveSubject.hospital("h2"), otherHospital);
        hub.register(LiveSubject.patient("p2"), otherPatient);

        hub.push(event);

        assertThat(sentText(hospital)).contains("\"type\":\"APPOINTMENT_BOOKED\"").contains("\"bookingId\":\"a1\"")
                .contains("\"appointmentDateTime\":\"2026-11-05T05:30:00Z\"");
        assertThat(sentText(patient)).contains("\"type\":\"APPOINTMENT_BOOKED\"");
        verify(otherHospital, never()).sendMessage(any());
        verify(otherPatient, never()).sendMessage(any());
    }

    @Test
    void register_refusesConnectionsOverThePerUserLimit() {
        LiveSubject subject = LiveSubject.hospital("h1");

        assertThat(hub.register(subject, session("s1", subject))).isTrue();
        assertThat(hub.register(subject, session("s2", subject))).isTrue();
        assertThat(hub.register(subject, session("s3", subject))).isFalse();
        assertThat(hub.connectionCount(subject)).isEqualTo(2);
    }

    @Test
    void unregister_removesTheConnection() throws IOException {
        LiveSubject subject = LiveSubject.patient("p1");
        WebSocketSession session = session("s1", subject);
        hub.register(subject, session);

        hub.unregister(session);
        hub.push(event);

        assertThat(hub.connectionCount(subject)).isZero();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void failedSend_dropsAndClosesTheConnection() throws IOException {
        LiveSubject subject = LiveSubject.hospital("h1");
        WebSocketSession session = session("s1", subject);
        doThrow(new IOException("broken pipe")).when(session).sendMessage(any());
        hub.register(subject, session);

        hub.push(event);

        assertThat(hub.connectionCount(subject)).isZero();
        verify(session).close(any());
    }

    @Test
    void heartbeat_pingsEveryConnection() throws IOException {
        LiveSubject subject = LiveSubject.hospital("h1");
        WebSocketSession session = session("s1", subject);
        hub.register(subject, session);

        hub.heartbeat();

        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PingMessage.class);
    }

    @Test
    void push_withNobodyConnected_doesNothing() {
        hub.push(event);

        assertThat(hub.connectionCount(LiveSubject.hospital("h1"))).isZero();
    }
}
