package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.model.AppointmentTokenClaim;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class AppointmentTokenGeneratorTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AppointmentTokenGenerator generator;

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(AppointmentTokenGenerator.ZONE).toInstant();
    }

    private void claimsInUse(Integer... tokens) {
        when(mongoTemplate.find(any(Query.class), eq(AppointmentTokenClaim.class)))
                .thenReturn(java.util.Arrays.stream(tokens)
                        .map(t -> AppointmentTokenClaim.builder().tokenNumber(t).build())
                        .toList());
    }

    private void legacyBookings(Integer... tokens) {
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class)))
                .thenReturn(java.util.Arrays.stream(tokens)
                        .map(t -> DoctorAppointmentBooking.builder().tokenNumber(t).build())
                        .toList());
    }

    @Test
    void claim_firstBookingOfDay_getsToken1KeyedByHospitalDoctorAndLocalDay() {
        claimsInUse();
        legacyBookings();

        // 00:30 IST on the 8th is still the 7th in UTC; the key must use the local day.
        int token = generator.claim("h1", "d1", local(2026, 10, 8, 0, 30), 30);

        assertThat(token).isEqualTo(1);
        ArgumentCaptor<AppointmentTokenClaim> claim = ArgumentCaptor.forClass(AppointmentTokenClaim.class);
        verify(mongoTemplate).insert(claim.capture());
        assertThat(claim.getValue().getId()).isEqualTo("h1:d1:2026-10-08:1");
    }

    @Test
    void claim_reusesLowestFreedNumber() {
        // 1..5 were issued, token 2 was cancelled (its claim deleted); legacy booking holds 3.
        claimsInUse(1, 4, 5);
        legacyBookings(3);

        assertThat(generator.claim("h1", "d1", local(2026, 10, 8, 10, 0), 30)).isEqualTo(2);
    }

    @Test
    void claim_allTokensTaken_throwsConflictWithoutInserting() {
        claimsInUse(1, 2);
        legacyBookings(3);

        assertThatThrownBy(() -> generator.claim("h1", "d1", local(2026, 10, 8, 10, 0), 3))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("All 3 appointment tokens");
        verify(mongoTemplate, never()).insert(any(AppointmentTokenClaim.class));
    }

    @Test
    void claim_zeroLimit_doctorNotAvailable() {
        assertThatThrownBy(() -> generator.claim("h1", "d1", local(2026, 10, 8, 10, 0), 0))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not taking appointments");
    }

    @Test
    void claim_racedForSameNumber_takesNextFree() {
        claimsInUse();
        legacyBookings();
        when(mongoTemplate.insert(argThat((AppointmentTokenClaim c) -> c != null && c.getTokenNumber() == 1)))
                .thenThrow(new DuplicateKeyException("taken"));

        assertThat(generator.claim("h1", "d1", local(2026, 10, 8, 10, 0), 2)).isEqualTo(2);
    }

    @Test
    void claim_racedForLastToken_throwsConflict() {
        claimsInUse(1);
        legacyBookings();
        when(mongoTemplate.insert(any(AppointmentTokenClaim.class))).thenThrow(new DuplicateKeyException("taken"));

        assertThatThrownBy(() -> generator.claim("h1", "d1", local(2026, 10, 8, 10, 0), 2))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void claim_unlimited_appendsAfterTakenNumbers() {
        claimsInUse(1, 2, 3);
        legacyBookings();

        assertThat(generator.claim("h1", "d1", local(2026, 10, 8, 10, 0), null)).isEqualTo(4);
    }

    @Test
    void release_deletesClaimForThatDay() {
        generator.release("h1", "d1", local(2026, 10, 8, 0, 30), 7);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(query.capture(), eq(AppointmentTokenClaim.class));
        assertThat(query.getValue().getQueryObject().get("_id")).isEqualTo("h1:d1:2026-10-08:7");
    }

    @Test
    void release_withoutToken_isNoOp() {
        generator.release("h1", "d1", local(2026, 10, 8, 10, 0), null);

        verify(mongoTemplate, never()).remove(any(Query.class), eq(AppointmentTokenClaim.class));
    }

    @Test
    void takenTokens_mergesClaimsAndLegacyBookings() {
        claimsInUse(1, 2);
        legacyBookings(2, 5);

        assertThat(generator.takenTokens("h1", "d1", LocalDate.of(2026, 10, 8))).containsExactly(1, 2, 5);
    }

    @Test
    void sameDay_usesLocalDayBoundaries() {
        assertThat(AppointmentTokenGenerator.sameDay(local(2026, 10, 8, 0, 30), local(2026, 10, 8, 23, 30)))
                .isTrue();
        assertThat(AppointmentTokenGenerator.sameDay(local(2026, 10, 8, 23, 30), local(2026, 10, 9, 0, 30)))
                .isFalse();
    }
}
