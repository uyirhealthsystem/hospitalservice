package com.uyir.hospital.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.uyir.hospital.event.BookingUpdateType;
import com.uyir.hospital.event.BookingUpdatedEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.security.Role;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// Real server, real WebSocket: the gateway headers on the upgrade request decide who receives what. No
// KAFKA_BROKERS in tests, so the local publisher delivers straight to this instance's connections.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private HospitalEventPublisher hospitalEventPublisher;

    @MockitoBean
    private HospitalRepository hospitalRepository;

    private static final class Inbox extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        String next() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("message within 5s").isNotNull();
            return message;
        }
    }

    private WebSocketSession connect(Inbox inbox, String userId, String role) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (userId != null) {
            headers.add("X-User-Id", userId);
            headers.add("X-User-Role", role);
        }
        return new StandardWebSocketClient()
                .execute(inbox, headers, URI.create("ws://localhost:" + port + LiveWebSocketConfig.PATH))
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void hospitalAndPatient_receiveTheirBookingUpdate_othersDoNot() throws Exception {
        when(hospitalRepository.findByUserId("hospital-user-1")).thenReturn(Optional.of(Hospital.builder().id("h1").build()));
        Inbox hospital = new Inbox();
        Inbox patient = new Inbox();
        Inbox otherPatient = new Inbox();
        WebSocketSession hospitalSession = connect(hospital, "hospital-user-1", "HOSPITAL");
        WebSocketSession patientSession = connect(patient, "p1", "PATIENT");
        WebSocketSession otherSession = connect(otherPatient, "p2", "PATIENT");

        assertThat(hospital.next()).contains("\"type\":\"CONNECTED\"").contains("\"id\":\"h1\"");
        assertThat(patient.next()).contains("\"type\":\"CONNECTED\"").contains("\"id\":\"p1\"");
        assertThat(otherPatient.next()).contains("\"type\":\"CONNECTED\"");

        hospitalEventPublisher.bookingUpdated(new BookingUpdatedEvent(BookingUpdateType.APPOINTMENT_BOOKED,
                BookingUpdatedEvent.BookingKind.APPOINTMENT, "a1", "h1", "p1", "John", "d1",
                Instant.parse("2026-11-05T05:30:00Z"), 4, null, "CONFIRMED", Role.PATIENT, Instant.now()));

        assertThat(hospital.next()).contains("\"type\":\"APPOINTMENT_BOOKED\"").contains("\"bookingId\":\"a1\"");
        assertThat(patient.next()).contains("\"type\":\"APPOINTMENT_BOOKED\"").contains("\"tokenNumber\":4");
        assertThat(otherPatient.messages.poll(500, TimeUnit.MILLISECONDS)).isNull();

        patientSession.sendMessage(new TextMessage("PING"));
        assertThat(patient.next()).isEqualTo("{\"type\":\"PONG\"}");

        hospitalSession.close(CloseStatus.NORMAL);
        patientSession.close(CloseStatus.NORMAL);
        otherSession.close(CloseStatus.NORMAL);
    }

    @Test
    void connectionWithoutGatewayHeaders_isRefused() {
        assertThatThrownBy(() -> connect(new Inbox(), null, null)).hasMessageContaining("403");
    }
}
