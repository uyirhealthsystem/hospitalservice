package com.uyir.hospital.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.security.Role;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class LiveHandshakeInterceptorTest {

    private final HospitalRepository hospitalRepository = mock(HospitalRepository.class);
    private final LiveHandshakeInterceptor interceptor = new LiveHandshakeInterceptor(hospitalRepository);

    private boolean handshake(String userId, String role, Map<String, Object> attributes, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", LiveWebSocketConfig.PATH);
        if (userId != null) {
            request.addHeader("X-User-Id", userId);
        }
        if (role != null) {
            request.addHeader("X-User-Role", role);
        }
        return interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                mock(WebSocketHandler.class), attributes);
    }

    @Test
    void hospitalUser_isResolvedToItsHospitalId() {
        when(hospitalRepository.findByUserId("u1")).thenReturn(Optional.of(Hospital.builder().id("h1").build()));
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake("u1", "HOSPITAL", attributes, new MockHttpServletResponse())).isTrue();
        assertThat(attributes.get(LiveSubject.ATTRIBUTE)).isEqualTo(new LiveSubject(Role.HOSPITAL, "h1"));
    }

    @Test
    void patient_isResolvedToItsUserId() {
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake("p1", "patient", attributes, new MockHttpServletResponse())).isTrue();
        assertThat(attributes.get(LiveSubject.ATTRIBUTE)).isEqualTo(LiveSubject.patient("p1"));
    }

    @Test
    void hospitalUserWithoutAHospital_isRefused() {
        when(hospitalRepository.findByUserId("u1")).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handshake("u1", "HOSPITAL", new HashMap<>(), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void missingHeaders_unknownOrAdminRole_areRefused() {
        assertThat(handshake(null, "PATIENT", new HashMap<>(), new MockHttpServletResponse())).isFalse();
        assertThat(handshake("p1", null, new HashMap<>(), new MockHttpServletResponse())).isFalse();
        assertThat(handshake("p1", "DOCTOR", new HashMap<>(), new MockHttpServletResponse())).isFalse();
        assertThat(handshake("a1", "ADMIN", new HashMap<>(), new MockHttpServletResponse())).isFalse();
    }
}
