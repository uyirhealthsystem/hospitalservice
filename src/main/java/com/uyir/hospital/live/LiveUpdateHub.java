package com.uyir.hospital.live;

import com.uyir.hospital.event.BookingUpdatedEvent;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

// The live WebSocket connections open on THIS pod, by hospital / patient. Like EmergencyAlertHub (SSE), every
// pod reads hospital.events itself (HospitalEventListener), so whichever pod a client is connected to delivers
// the update. Idle connections cost no thread; each send is bounded so a slow client can't hold memory.
@Component
public class LiveUpdateHub {

    static final String SESSION_ATTRIBUTE = "liveSession";

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateHub.class);

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;
    private final LiveProperties properties;

    public LiveUpdateHub(JsonMapper jsonMapper, LiveProperties properties) {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    // Returns false (and the caller closes the connection) once the subject already has the maximum open here.
    boolean register(LiveSubject subject, WebSocketSession session) {
        // Thread-safe sends (Kafka listener + heartbeat + replies), bounded in time and queued bytes. TERMINATE:
        // over the limit the connection is closed and the client reconnects, rather than dropping messages silently.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session,
                (int) properties.sendTimeLimit().toMillis(), (int) properties.sendBufferLimit().toBytes(),
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE);
        boolean[] added = {false};
        sessions.compute(subject.key(), (key, open) -> {
            Set<WebSocketSession> set = open != null ? open : ConcurrentHashMap.newKeySet();
            if (set.size() < properties.maxSessionsPerUser()) {
                set.add(safe);
                added[0] = true;
            }
            return set.isEmpty() ? null : set;
        });
        if (added[0]) {
            session.getAttributes().put(SESSION_ATTRIBUTE, safe);
        }
        return added[0];
    }

    void unregister(WebSocketSession session) {
        Object subject = session.getAttributes().get(LiveSubject.ATTRIBUTE);
        Object safe = session.getAttributes().get(SESSION_ATTRIBUTE);
        if (subject instanceof LiveSubject live && safe instanceof WebSocketSession registered) {
            remove(live.key(), registered);
        }
    }

    // The decorated (thread-safe) session for a connection, for replies from the handler.
    WebSocketSession sessionFor(WebSocketSession session) {
        Object safe = session.getAttributes().get(SESSION_ATTRIBUTE);
        return safe instanceof WebSocketSession registered ? registered : session;
    }

    // Sends the update to the hospital's and the patient's open connections on this pod.
    public void push(BookingUpdatedEvent event) {
        Set<WebSocketSession> hospital = event.hospitalId() == null ? null
                : sessions.get(LiveSubject.hospital(event.hospitalId()).key());
        Set<WebSocketSession> patient = event.patientId() == null ? null
                : sessions.get(LiveSubject.patient(event.patientId()).key());
        if (hospital == null && patient == null) {
            return;
        }
        TextMessage message = new TextMessage(jsonMapper.writeValueAsString(event));
        if (hospital != null) {
            sendAll(LiveSubject.hospital(event.hospitalId()).key(), hospital, message);
        }
        if (patient != null) {
            sendAll(LiveSubject.patient(event.patientId()).key(), patient, message);
        }
    }

    void send(WebSocketSession session, Map<String, ?> payload) {
        Object subject = session.getAttributes().get(LiveSubject.ATTRIBUTE);
        String key = subject instanceof LiveSubject live ? live.key() : "";
        send(key, sessionFor(session), new TextMessage(jsonMapper.writeValueAsString(payload)));
    }

    // The gateway drops a connection idle for 15s; a ping frame keeps it open (clients answer with pong automatically).
    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        PingMessage ping = new PingMessage(ByteBuffer.allocate(0));
        sessions.forEach((key, open) -> open.forEach(session -> send(key, session, ping)));
    }

    int connectionCount(LiveSubject subject) {
        Set<WebSocketSession> open = sessions.get(subject.key());
        return open == null ? 0 : open.size();
    }

    // On shutdown close every connection with 1001 (going away) so clients reconnect to another pod right away.
    @PreDestroy
    void closeAll() {
        sessions.values().forEach(open -> open.forEach(session -> close(session, CloseStatus.GOING_AWAY)));
        sessions.clear();
    }

    private void sendAll(String key, Set<WebSocketSession> open, WebSocketMessage<?> message) {
        for (WebSocketSession session : open) {
            send(key, session, message);
        }
    }

    private void send(String key, WebSocketSession session, WebSocketMessage<?> message) {
        if (!session.isOpen()) {
            remove(key, session);
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | RuntimeException ex) {
            // Gone (app closed, network dropped) or over the send limits (SessionLimitExceededException).
            log.debug("Dropping live connection '{}' for {}: {}", session.getId(), key, ex.getMessage());
            remove(key, session);
            close(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private void remove(String key, WebSocketSession session) {
        sessions.computeIfPresent(key, (k, open) -> {
            open.remove(session);
            return open.isEmpty() ? null : open;
        });
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException | RuntimeException ignored) {
            // Already closing.
        }
    }
}
