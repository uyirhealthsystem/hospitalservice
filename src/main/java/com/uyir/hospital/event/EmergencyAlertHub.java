package com.uyir.hospital.event;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// The hospital dashboards connected to THIS pod, by hospital id. A hospital can have several open
// (front desk, casualty, a phone). With several pods each one holds only its own connections, which
// is why every pod consumes hospital.events itself (HospitalEventListener) instead of relying on the
// pod that took the booking.
@Component
public class EmergencyAlertHub {

    public static final String EMERGENCY_BOOKING_REQUESTED = "emergency-booking-requested";

    private static final Logger log = LoggerFactory.getLogger(EmergencyAlertHub.class);

    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public EmergencyAlertHub(HospitalEventProperties properties) {
        this.timeoutMillis = properties.sseTimeout().toMillis();
    }

    public SseEmitter subscribe(String hospitalId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.computeIfAbsent(hospitalId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(hospitalId, emitter));
        emitter.onTimeout(() -> remove(hospitalId, emitter));
        emitter.onError(ex -> remove(hospitalId, emitter));
        // Sent straight away so the response headers go out now - the gateway and load balancer then see
        // a live stream rather than a request still waiting for its first byte.
        send(hospitalId, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    public void push(EmergencyBookingRequestedEvent event) {
        Set<SseEmitter> open = emitters.get(event.hospitalId());
        if (open == null) {
            return;
        }
        for (SseEmitter emitter : open) {
            send(event.hospitalId(), emitter, SseEmitter.event()
                    .id(event.bookingId())
                    .name(EMERGENCY_BOOKING_REQUESTED)
                    .data(event, MediaType.APPLICATION_JSON));
        }
    }

    // The gateway drops a connection that is idle for 15s (UPSTREAM_TIMEOUT_MS), so ping well inside that.
    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        emitters.forEach((hospitalId, open) ->
                open.forEach(emitter -> send(hospitalId, emitter, SseEmitter.event().comment("ping"))));
    }

    int connectionCount(String hospitalId) {
        Set<SseEmitter> open = emitters.get(hospitalId);
        return open == null ? 0 : open.size();
    }

    // On shutdown close every stream cleanly, so dashboards reconnect to another pod right away.
    @PreDestroy
    void closeAll() {
        emitters.values().forEach(open -> open.forEach(SseEmitter::complete));
        emitters.clear();
    }

    private void send(String hospitalId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            // The dashboard went away (tab closed, network dropped) or the emitter already completed.
            log.debug("Dropping closed alert stream for hospital '{}': {}", hospitalId, ex.getMessage());
            remove(hospitalId, emitter);
            emitter.completeWithError(ex);
        }
    }

    private void remove(String hospitalId, SseEmitter emitter) {
        emitters.computeIfPresent(hospitalId, (id, open) -> {
            open.remove(emitter);
            return open.isEmpty() ? null : open;
        });
    }
}
