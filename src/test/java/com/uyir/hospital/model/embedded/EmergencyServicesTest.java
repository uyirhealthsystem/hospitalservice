package com.uyir.hospital.model.embedded;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class EmergencyServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void onAt_leaseRunning_isOn() {
        EmergencyServices es = EmergencyServices.builder().handlesEmergencies(true).emergencyOnUntil(NOW.plusSeconds(1)).build();
        assertThat(es.onAt(NOW)).isTrue();
    }

    @Test
    void onAt_leaseEnded_isOffEvenBeforeTheJobRuns() {
        EmergencyServices es = EmergencyServices.builder().handlesEmergencies(true).emergencyOnUntil(NOW).build();
        assertThat(es.onAt(NOW)).isFalse();
    }

    @Test
    void onAt_flagOff_isOff() {
        EmergencyServices es = EmergencyServices.builder().handlesEmergencies(false).emergencyOnUntil(NOW.plusSeconds(60)).build();
        assertThat(es.onAt(NOW)).isFalse();
    }

    @Test
    void onAt_legacyWithoutLease_staysOnUntilTheJobGivesOne() {
        EmergencyServices es = EmergencyServices.builder().handlesEmergencies(true).build();
        assertThat(es.onAt(NOW)).isTrue();
    }

    @Test
    void handlesEmergencyType_expiredLease_doesNotHandle() {
        EmergencyServices es = EmergencyServices.builder()
                .handlesEmergencies(true)
                .emergencyOnUntil(Instant.now().minusSeconds(1))
                .specialtyEmergencyConditionsHandled(List.of("Burns"))
                .build();
        assertThat(es.handlesEmergencyType("Burns")).isFalse();
    }

    @Test
    void json_showsLeaseButNeverAcceptsItOrTheWarningField() {
        // Same null-for-primitives tolerance as the app (JacksonConfig).
        JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
        EmergencyServices es = EmergencyServices.builder()
                .handlesEmergencies(true).emergencyOnUntil(NOW).expiryWarningSentAt(NOW).build();

        String json = mapper.writeValueAsString(es);
        assertThat(json).contains("\"emergencyOnUntil\":\"2026-11-09T12:00:00Z\"").doesNotContain("expiryWarningSentAt");

        EmergencyServices fromClient = mapper.readValue(
                "{\"handlesEmergencies\":true,\"emergencyOnUntil\":\"2099-01-01T00:00:00Z\",\"expiryWarningSentAt\":\"2099-01-01T00:00:00Z\"}",
                EmergencyServices.class);
        assertThat(fromClient.getEmergencyOnUntil()).isNull();
        assertThat(fromClient.getExpiryWarningSentAt()).isNull();
    }
}
