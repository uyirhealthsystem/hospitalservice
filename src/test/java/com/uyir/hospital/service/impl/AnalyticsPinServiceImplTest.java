package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.AnalyticsPinResetResponse;
import com.uyir.hospital.dto.AnalyticsPinStatusResponse;
import com.uyir.hospital.event.AnalyticsPinResetCodeIssuedEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.exception.AnalyticsPinException;
import com.uyir.hospital.exception.AnalyticsPinException.Code;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.TooManyAttemptsException;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.repository.HospitalRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AnalyticsPinServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    @Mock
    private HospitalRepository hospitalRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private HospitalEventPublisher eventPublisher;

    // Low cost so the tests stay fast; production uses the default strength.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AnalyticsPinServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsPinServiceImpl(hospitalRepository, mongoTemplate, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom(), encoder);
    }

    private static Hospital hospital() {
        return Hospital.builder()
                .id("h1")
                .userId("u1")
                .hospitalName("City Care")
                .contactDetails(ContactDetails.builder().email("admin@city.in").build())
                .build();
    }

    private Hospital withPin(String pin) {
        Hospital hospital = hospital();
        hospital.setAnalyticsPinHash(encoder.encode(pin));
        hospital.setAnalyticsPinUpdatedAt(NOW.minusSeconds(86400));
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        return hospital;
    }

    // findAndModify returns the hospital with the update's $set / $inc applied, like returnNew(true).
    private void applyUpdatesTo(Hospital hospital) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Hospital.class)))
                .thenAnswer(invocation -> {
                    Document update = ((Update) invocation.getArgument(1)).getUpdateObject();
                    Document set = (Document) update.get("$set");
                    Document inc = (Document) update.get("$inc");
                    if (set != null) {
                        if (set.containsKey("analyticsPinHash")) {
                            hospital.setAnalyticsPinHash((String) set.get("analyticsPinHash"));
                            hospital.setAnalyticsPinUpdatedAt((Instant) set.get("analyticsPinUpdatedAt"));
                            hospital.setAnalyticsPinFailedAttempts(0);
                            hospital.setAnalyticsPinLockedUntil(null);
                        }
                    }
                    if (inc != null && inc.containsKey("analyticsPinFailedAttempts")) {
                        hospital.setAnalyticsPinFailedAttempts(hospital.getAnalyticsPinFailedAttempts() + 1);
                    }
                    if (inc != null && inc.containsKey("analyticsPinResetAttempts")) {
                        hospital.setAnalyticsPinResetAttempts(hospital.getAnalyticsPinResetAttempts() + 1);
                    }
                    return hospital;
                });
    }

    // ── PIN rules ──────────────────────────────────────────────────────────────

    @Test
    void validateNewPin_acceptsFourToSixDigits() {
        assertThat(AnalyticsPinServiceImpl.validateNewPin(" 4821 ")).isEqualTo("4821");
        assertThat(AnalyticsPinServiceImpl.validateNewPin("739514")).isEqualTo("739514");
    }

    @Test
    void validateNewPin_rejectsWrongLengthLettersAndEasyPins() {
        for (String bad : new String[] {"123", "1234567", "12a4", "", "0000", "1234", "4321", "123456", "987654"}) {
            assertThatThrownBy(() -> AnalyticsPinServiceImpl.validateNewPin(bad))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── set ────────────────────────────────────────────────────────────────────

    @Test
    void setPin_storesOnlyHash() {
        Hospital hospital = hospital();
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        applyUpdatesTo(hospital);

        AnalyticsPinStatusResponse status = service.setPin("h1", "4821");

        assertThat(status.pinSet()).isTrue();
        assertThat(status.pinUpdatedAt()).isEqualTo(NOW);
        assertThat(hospital.getAnalyticsPinHash()).isNotEqualTo("4821");
        assertThat(encoder.matches("4821", hospital.getAnalyticsPinHash())).isTrue();
    }

    @Test
    void setPin_whenAlreadySet_conflicts() {
        withPin("4821");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Hospital.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.setPin("h1", "7395"))
                .isInstanceOfSatisfying(AnalyticsPinException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Code.ANALYTICS_PIN_ALREADY_SET));
    }

    // ── verify ─────────────────────────────────────────────────────────────────

    @Test
    void verify_correctPin_passes() {
        withPin("4821");
        assertThatCode(() -> service.verify("h1", "4821")).doesNotThrowAnyException();
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Hospital.class));
    }

    @Test
    void verify_correctPin_clearsEarlierWrongAttempts() {
        withPin("4821").setAnalyticsPinFailedAttempts(2);
        service.verify("h1", "4821");
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Hospital.class));
    }

    @Test
    void verify_noPinSet_isNotSet_evenWithoutPin() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital()));
        assertThatThrownBy(() -> service.verify("h1", null))
                .isInstanceOfSatisfying(AnalyticsPinException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Code.ANALYTICS_PIN_NOT_SET));
    }

    @Test
    void verify_missingPin_isRequired() {
        withPin("4821");
        assertThatThrownBy(() -> service.verify("h1", " "))
                .isInstanceOfSatisfying(AnalyticsPinException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Code.ANALYTICS_PIN_REQUIRED));
    }

    @Test
    void verify_wrongPin_countsAndSaysAttemptsLeft() {
        Hospital hospital = withPin("4821");
        applyUpdatesTo(hospital);

        assertThatThrownBy(() -> service.verify("h1", "1111"))
                .isInstanceOfSatisfying(AnalyticsPinException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(Code.ANALYTICS_PIN_INCORRECT);
                    assertThat(ex.getMessage()).isEqualTo("Incorrect PIN. 4 attempts left.");
                });
        assertThat(hospital.getAnalyticsPinFailedAttempts()).isEqualTo(1);
    }

    @Test
    void verify_fifthWrongPin_locksForFifteenMinutes() {
        Hospital hospital = withPin("4821");
        hospital.setAnalyticsPinFailedAttempts(4);
        applyUpdatesTo(hospital);

        assertThatThrownBy(() -> service.verify("h1", "1111"))
                .isInstanceOfSatisfying(AnalyticsPinException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(Code.ANALYTICS_PIN_LOCKED);
                    assertThat(ex.getMessage()).contains("15 minutes");
                });
        ArgumentCaptor<Update> lock = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), lock.capture(), eq(Hospital.class));
        Document set = (Document) lock.getValue().getUpdateObject().get("$set");
        assertThat(set.get("analyticsPinLockedUntil")).isEqualTo(NOW.plus(AnalyticsPinServiceImpl.LOCK_DURATION));
        assertThat(set.get("analyticsPinFailedAttempts")).isEqualTo(0);
    }

    @Test
    void verify_whileLocked_rejectsEvenTheRightPin() {
        withPin("4821").setAnalyticsPinLockedUntil(NOW.plusSeconds(300));

        assertThatThrownBy(() -> service.verify("h1", "4821"))
                .isInstanceOfSatisfying(AnalyticsPinException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(Code.ANALYTICS_PIN_LOCKED);
                    assertThat(ex.getMessage()).contains("5 minutes");
                });
    }

    @Test
    void verify_afterLockExpires_acceptsTheRightPin() {
        withPin("4821").setAnalyticsPinLockedUntil(NOW.minusSeconds(1));
        assertThatCode(() -> service.verify("h1", "4821")).doesNotThrowAnyException();
    }

    // ── change ─────────────────────────────────────────────────────────────────

    @Test
    void changePin_withRightCurrentPin_replacesHash() {
        Hospital hospital = withPin("4821");
        applyUpdatesTo(hospital);

        service.changePin("h1", "4821", "7395");

        assertThat(encoder.matches("7395", hospital.getAnalyticsPinHash())).isTrue();
    }

    @Test
    void changePin_wrongCurrentPin_countsAsWrongAttempt() {
        Hospital hospital = withPin("4821");
        applyUpdatesTo(hospital);

        assertThatThrownBy(() -> service.changePin("h1", "1111", "7395"))
                .isInstanceOfSatisfying(AnalyticsPinException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Code.ANALYTICS_PIN_INCORRECT));
        assertThat(encoder.matches("4821", hospital.getAnalyticsPinHash())).isTrue();
    }

    @Test
    void changePin_badNewPin_failsBeforeCheckingCurrent() {
        assertThatThrownBy(() -> service.changePin("h1", "4821", "0000")).isInstanceOf(IllegalArgumentException.class);
        verify(hospitalRepository, never()).findById(any());
    }

    @Test
    void changePin_sameAsCurrent_rejected() {
        withPin("4821");
        assertThatThrownBy(() -> service.changePin("h1", "4821", "4821")).isInstanceOf(IllegalArgumentException.class);
    }

    // ── reset ──────────────────────────────────────────────────────────────────

    @Test
    void requestReset_emailsSixDigitCode_storesOnlyHash() {
        withPin("4821");

        AnalyticsPinResetResponse response = service.requestReset("h1");

        ArgumentCaptor<AnalyticsPinResetCodeIssuedEvent> event = ArgumentCaptor.forClass(AnalyticsPinResetCodeIssuedEvent.class);
        verify(eventPublisher).analyticsPinResetCodeIssued(event.capture());
        assertThat(event.getValue().resetCode()).matches("\\d{6}");
        assertThat(event.getValue().email()).isEqualTo("admin@city.in");
        assertThat(response.codeSentTo()).isEqualTo("ad***@city.in");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(AnalyticsPinServiceImpl.RESET_CODE_TTL));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(Hospital.class));
        String hash = (String) ((Document) update.getValue().getUpdateObject().get("$set")).get("analyticsPinResetCodeHash");
        assertThat(hash).isNotEqualTo(event.getValue().resetCode());
        assertThat(encoder.matches(event.getValue().resetCode(), hash)).isTrue();
    }

    @Test
    void requestReset_noPin_conflicts() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital()));
        assertThatThrownBy(() -> service.requestReset("h1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void requestReset_noEmail_conflicts() {
        withPin("4821").setContactDetails(null);
        assertThatThrownBy(() -> service.requestReset("h1")).isInstanceOf(ConflictException.class);
        verify(eventPublisher, never()).analyticsPinResetCodeIssued(any());
    }

    @Test
    void requestReset_tooSoon_429() {
        withPin("4821").setAnalyticsPinResetCodeIssuedAt(NOW.minusSeconds(10));
        assertThatThrownBy(() -> service.requestReset("h1")).isInstanceOf(TooManyAttemptsException.class);
    }

    private Hospital withResetCode(String code, Instant expiresAt, int attempts) {
        Hospital hospital = withPin("4821");
        hospital.setAnalyticsPinResetCodeHash(encoder.encode(code));
        hospital.setAnalyticsPinResetCodeIssuedAt(NOW.minusSeconds(60));
        hospital.setAnalyticsPinResetCodeExpiresAt(expiresAt);
        hospital.setAnalyticsPinResetAttempts(attempts);
        return hospital;
    }

    @Test
    void confirmReset_rightCode_setsNewPin_andClearsLock() {
        Hospital hospital = withResetCode("482913", NOW.plusSeconds(600), 0);
        hospital.setAnalyticsPinLockedUntil(NOW.plusSeconds(600));
        applyUpdatesTo(hospital);

        AnalyticsPinStatusResponse status = service.confirmReset("h1", "482 913", "7395");

        assertThat(encoder.matches("7395", hospital.getAnalyticsPinHash())).isTrue();
        assertThat(status.lockedUntil()).isNull();
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Hospital.class));
        Document unset = (Document) update.getValue().getUpdateObject().get("$unset");
        assertThat(unset).containsKeys("analyticsPinResetCodeHash", "analyticsPinLockedUntil");
    }

    @Test
    void confirmReset_wrongCode_countsAttempts() {
        Hospital hospital = withResetCode("482913", NOW.plusSeconds(600), 0);
        applyUpdatesTo(hospital);

        assertThatThrownBy(() -> service.confirmReset("h1", "111111", "7395"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Incorrect reset code. 4 attempts left.");
    }

    @Test
    void confirmReset_expired_conflicts() {
        withResetCode("482913", NOW.minusSeconds(1), 0);
        assertThatThrownBy(() -> service.confirmReset("h1", "482913", "7395")).isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmReset_tooManyWrongCodes_429() {
        withResetCode("482913", NOW.plusSeconds(600), 5);
        assertThatThrownBy(() -> service.confirmReset("h1", "482913", "7395")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void confirmReset_noActiveCode_conflicts() {
        withPin("4821");
        assertThatThrownBy(() -> service.confirmReset("h1", "482913", "7395")).isInstanceOf(ConflictException.class);
    }
}
