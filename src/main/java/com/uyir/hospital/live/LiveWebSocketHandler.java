package com.uyir.hospital.live;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// /api/v1/hospital/ws. Server -> client: {"type":"CONNECTED",...} once, then one JSON BookingUpdatedEvent per
// booking change. Client -> server: "PING" (or {"type":"PING"}) gets {"type":"PONG"} - for clients that can't
// see protocol-level pings; anything else is ignored. Bookings themselves still change only through REST.
@Component
public class LiveWebSocketHandler extends TextWebSocketHandler {

    private final LiveUpdateHub hub;

    public LiveWebSocketHandler(LiveUpdateHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (!(session.getAttributes().get(LiveSubject.ATTRIBUTE) instanceof LiveSubject subject)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!hub.register(subject, session)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Too many open connections"));
            return;
        }
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("type", "CONNECTED");
        hello.put("role", subject.role());
        hello.put("id", subject.id());
        hub.send(session, hello);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String text = message.getPayload().trim();
        if (text.equalsIgnoreCase("PING") || text.replace(" ", "").equalsIgnoreCase("{\"type\":\"PING\"}")) {
            hub.send(session, Map.of("type", "PONG"));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        hub.unregister(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregister(session);
    }
}
