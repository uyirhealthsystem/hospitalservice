package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.model.AppointmentTokenCounter;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class AppointmentTokenGeneratorTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AppointmentTokenGenerator generator;

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(AppointmentTokenGenerator.ZONE).toInstant();
    }

    @Test
    void nextToken_atomicallyIncrementsCounterKeyedByDoctorAndLocalDay() {
        when(mongoTemplate.findAndModify(
                        any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                        eq(AppointmentTokenCounter.class)))
                .thenReturn(AppointmentTokenCounter.builder().id("d1:2026-10-08").seq(3).build());

        // 00:30 IST on the 8th is still the 7th in UTC; the key must use the local day.
        int token = generator.nextToken("d1", local(2026, 10, 8, 0, 30));

        assertThat(token).isEqualTo(3);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(
                query.capture(), any(Update.class), options.capture(), eq(AppointmentTokenCounter.class));
        assertThat(query.getValue().getQueryObject().get("_id")).isEqualTo("d1:2026-10-08");
        assertThat(options.getValue().isUpsert()).isTrue();
        assertThat(options.getValue().isReturnNew()).isTrue();
    }

    @Test
    void sameDay_usesLocalDayBoundaries() {
        assertThat(AppointmentTokenGenerator.sameDay(local(2026, 10, 8, 0, 30), local(2026, 10, 8, 23, 30)))
                .isTrue();
        assertThat(AppointmentTokenGenerator.sameDay(local(2026, 10, 8, 23, 30), local(2026, 10, 9, 0, 30)))
                .isFalse();
    }
}
