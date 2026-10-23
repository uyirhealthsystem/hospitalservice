package com.uyir.hospital.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitterReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class EmergencyAlertHubTest {

    private EmergencyAlertHub hub;

    @BeforeEach
    void setUp() {
        hub = new EmergencyAlertHub(new HospitalEventProperties("local", "hospital.events", Duration.ofMinutes(30)));
    }

    private EmergencyBookingRequestedEvent event(String hospitalId) {
        return new EmergencyBookingRequestedEvent("b1", hospitalId, "City Hospital", "er@city.test",
                "John Doe", "9999999999", "Cardiac Arrest", Instant.parse("2026-10-19T10:00:00Z"));
    }

    @Test
    void subscribe_tracksConnectionsPerHospital() {
        hub.subscribe("h1");
        hub.subscribe("h1");
        hub.subscribe("h2");

        assertThat(hub.connectionCount("h1")).isEqualTo(2);
        assertThat(hub.connectionCount("h2")).isEqualTo(1);
        assertThat(hub.connectionCount("h3")).isZero();
    }

    @Test
    void push_reachesOnlyThatHospitalsDashboards() throws Exception {
        MockHttpServletResponse h1Response = new MockHttpServletResponse();
        MockHttpServletResponse h2Response = new MockHttpServletResponse();
        attach(hub.subscribe("h1"), h1Response);
        attach(hub.subscribe("h2"), h2Response);

        hub.push(event("h1"));

        String h1Stream = h1Response.getContentAsString();
        assertThat(h1Stream).contains("event:" + EmergencyAlertHub.EMERGENCY_BOOKING_REQUESTED);
        assertThat(h1Stream).contains("id:b1");
        assertThat(h1Stream).contains("\"emergencyType\":\"Cardiac Arrest\"");
        assertThat(h1Stream).contains("\"patientPhone\":\"9999999999\"");
        assertThat(h2Response.getContentAsString()).doesNotContain(EmergencyAlertHub.EMERGENCY_BOOKING_REQUESTED);
    }

    @Test
    void push_withNoOpenDashboards_isANoOp() {
        hub.push(event("nobody-listening"));

        assertThat(hub.connectionCount("nobody-listening")).isZero();
    }

    @Test
    void completedStream_isRemoved() {
        SseEmitter emitter = hub.subscribe("h1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        attach(emitter, response);

        emitter.complete();
        hub.push(event("h1"));

        assertThat(hub.connectionCount("h1")).isZero();
    }

    // Binds the emitter to a mock servlet response, as Spring MVC does when the controller returns it.
    private static void attach(SseEmitter emitter, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        asyncManager.setAsyncWebRequest(new StandardServletAsyncWebRequest(request, response));
        try {
            new ResponseBodyEmitterReturnValueHandler(
                    List.of(new StringHttpMessageConverter(), new JacksonJsonHttpMessageConverter()))
                    .handleReturnValue(emitter,
                            new MethodParameter(EmergencyAlertHubTest.class.getDeclaredMethod("emitterReturnType"), -1),
                            new ModelAndViewContainer(),
                            new ServletWebRequest(request, response));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unused")
    private static SseEmitter emitterReturnType() {
        return null;
    }
}
