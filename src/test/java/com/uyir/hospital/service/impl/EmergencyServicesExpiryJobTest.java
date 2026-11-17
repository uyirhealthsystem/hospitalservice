package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.uyir.hospital.event.EmergencyServicesExpiringEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.service.EmergencyServicesProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class EmergencyServicesExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final HospitalEventPublisher publisher = mock(HospitalEventPublisher.class);
    private final EmergencyServicesExpiryJob job = new EmergencyServicesExpiryJob(mongoTemplate, publisher,
            new EmergencyServicesProperties(Duration.ofHours(6), Duration.ofMinutes(15)));

    private static UpdateResult modified(long count) {
        return UpdateResult.acknowledged(count, count, null);
    }

    @Test
    void switchOffExpired_onlyMatchesEndedLeasesAndClearsThem() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Hospital.class))).thenReturn(modified(3));

        assertThat(job.switchOffExpired(NOW)).isEqualTo(3);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(Hospital.class));
        Document filter = query.getValue().getQueryObject();
        assertThat(filter.get("emergencyServices.handlesEmergencies")).isEqualTo(true);
        assertThat(filter.get("emergencyServices.emergencyOnUntil")).isEqualTo(new Document("$lte", NOW));
        Document set = update.getValue().getUpdateObject().get("$set", Document.class);
        assertThat(set.get("emergencyServices.handlesEmergencies")).isEqualTo(false);
        assertThat(update.getValue().getUpdateObject().get("$unset", Document.class))
                .containsKeys("emergencyServices.emergencyOnUntil", "emergencyServices.expiryWarningSentAt");
    }

    @Test
    void grantMissingLeases_givesLegacyHospitalsAFullLeaseFromNow() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Hospital.class))).thenReturn(modified(2));

        assertThat(job.grantMissingLeases(NOW)).isEqualTo(2);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(Hospital.class));
        Document filter = query.getValue().getQueryObject();
        assertThat(filter.containsKey("emergencyServices.emergencyOnUntil")).isTrue();
        assertThat(filter.get("emergencyServices.emergencyOnUntil")).isNull();
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class).get("emergencyServices.emergencyOnUntil"))
                .isEqualTo(NOW.plus(Duration.ofHours(6)));
    }

    @Test
    void warnExpiringSoon_publishesOncePerClaimedLease() {
        Instant onUntil = NOW.plus(Duration.ofMinutes(10));
        Hospital h1 = hospital("h1", onUntil);
        Hospital h2 = hospital("h2", onUntil);
        when(mongoTemplate.find(any(Query.class), eq(Hospital.class))).thenReturn(List.of(h1, h2));
        // Another pod claimed h2 first.
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Hospital.class)))
                .thenReturn(modified(1), modified(0));

        assertThat(job.warnExpiringSoon(NOW)).isEqualTo(1);

        ArgumentCaptor<EmergencyServicesExpiringEvent> event = ArgumentCaptor.forClass(EmergencyServicesExpiringEvent.class);
        verify(publisher, times(1)).emergencyServicesExpiring(event.capture());
        assertThat(event.getValue().hospitalId()).isEqualTo("h1");
        assertThat(event.getValue().emergencyOnUntil()).isEqualTo(onUntil);
    }

    @Test
    void warnExpiringSoon_onlyLooksAtLeasesEndingWithinTheWarningWindow() {
        when(mongoTemplate.find(any(Query.class), eq(Hospital.class))).thenReturn(List.of());

        job.warnExpiringSoon(NOW);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Hospital.class));
        Document window = query.getValue().getQueryObject().get("emergencyServices.emergencyOnUntil", Document.class);
        assertThat(window.get("$gt")).isEqualTo(NOW);
        assertThat(window.get("$lte")).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        verify(publisher, never()).emergencyServicesExpiring(any());
    }

    @Test
    void run_survivesMongoFailure() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Hospital.class)))
                .thenThrow(new RuntimeException("mongo down"));

        job.run();

        verify(publisher, never()).emergencyServicesExpiring(any());
    }

    private static Hospital hospital(String id, Instant onUntil) {
        return Hospital.builder()
                .id(id)
                .hospitalName("Hospital " + id)
                .emergencyServices(EmergencyServices.builder().handlesEmergencies(true).emergencyOnUntil(onUntil).build())
                .build();
    }
}
