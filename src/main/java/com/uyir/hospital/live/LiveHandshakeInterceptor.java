package com.uyir.hospital.live;

import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

// Decides who a live connection belongs to, from the X-User-Id / X-User-Role headers the gateway puts on the
// upgrade request after validating the token - the same trust model as every REST endpoint (CurrentUserContext).
// HOSPITAL -> its hospital id (403 if it has not registered one); PATIENT -> its user id; anyone else -> 403.
@Component
public class LiveHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LiveHandshakeInterceptor.class);

    private final HospitalRepository hospitalRepository;

    public LiveHandshakeInterceptor(HospitalRepository hospitalRepository) {
        this.hospitalRepository = hospitalRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Optional<LiveSubject> subject = resolve(
                request.getHeaders().getFirst(CurrentUserContext.USER_ID_HEADER),
                request.getHeaders().getFirst(CurrentUserContext.USER_ROLE_HEADER));
        if (subject.isEmpty()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(LiveSubject.ATTRIBUTE, subject.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }

    Optional<LiveSubject> resolve(String userId, String roleHeader) {
        if (userId == null || userId.isBlank() || roleHeader == null || roleHeader.isBlank()) {
            log.debug("Live connection refused: missing {} / {} header",
                    CurrentUserContext.USER_ID_HEADER, CurrentUserContext.USER_ROLE_HEADER);
            return Optional.empty();
        }
        Role role;
        try {
            role = Role.valueOf(roleHeader.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return switch (role) {
            case PATIENT -> Optional.of(LiveSubject.patient(userId));
            case HOSPITAL -> hospitalRepository.findByUserId(userId).map(Hospital::getId).map(LiveSubject::hospital);
            default -> Optional.empty();
        };
    }
}
