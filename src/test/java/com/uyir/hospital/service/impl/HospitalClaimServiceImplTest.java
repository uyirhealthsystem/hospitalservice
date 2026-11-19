package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.ClaimHospitalRequest;
import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.event.HospitalClaimCodeIssuedEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.TooManyAttemptsException;
import com.uyir.hospital.mapper.HospitalMapper;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalService;
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
class HospitalClaimServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    @Mock
    private HospitalService hospitalService;
    @Mock
    private HospitalRepository hospitalRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private HospitalEventPublisher eventPublisher;

    // Low cost so the tests stay fast; production uses the default strength.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private HospitalClaimServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HospitalClaimServiceImpl(hospitalService, hospitalRepository, mongoTemplate, eventPublisher,
                new HospitalMapper(), Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom(), encoder);
    }

    private static Hospital unclaimed(String email) {
        return Hospital.builder()
                .id("h1")
                .hospitalName("City Care")
                .onboardedByProUserId("pro-1")
                .contactDetails(ContactDetails.builder().phone("+91-9876543210").email(email).build())
                .build();
    }

    // findAndModify(issueCode) returns the hospital with the $set values applied, like returnNew(true).
    private void answerFindAndModifyWithSetApplied(Hospital base) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Hospital.class)))
                .thenAnswer(invocation -> {
                    Document set = (Document) ((Update) invocation.getArgument(1)).getUpdateObject().get("$set");
                    base.setClaimCodeHash((String) set.get("claimCodeHash"));
                    base.setClaimCodeIssuedAt((Instant) set.get("claimCodeIssuedAt"));
                    base.setClaimCodeExpiresAt((Instant) set.get("claimCodeExpiresAt"));
                    base.setClaimAttempts(0);
                    return base;
                });
    }

    private static HospitalResponse created() {
        return HospitalResponse.builder().id("h1").hospitalName("City Care").build();
    }

    private Hospital withCode(String code, Instant expiresAt, int attempts) {
        Hospital hospital = unclaimed("admin@city.in");
        hospital.setClaimCodeHash(encoder.encode(code));
        hospital.setClaimCodeIssuedAt(NOW.minusSeconds(3600));
        hospital.setClaimCodeExpiresAt(expiresAt);
        hospital.setClaimAttempts(attempts);
        return hospital;
    }

    // ── onboard ────────────────────────────────────────────────────────────────

    @Test
    void onboard_createsUnownedHospital_storesOnlyHash_andEmailsTheCode() {
        Hospital hospital = unclaimed("admin@city.in");
        when(hospitalService.createOnboarded(eq("pro-1"), any(HospitalRequest.class))).thenReturn(created());
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        answerFindAndModifyWithSetApplied(hospital);

        HospitalResponse response = service.onboard("pro-1", HospitalRequest.builder().build());

        ArgumentCaptor<HospitalClaimCodeIssuedEvent> event = ArgumentCaptor.forClass(HospitalClaimCodeIssuedEvent.class);
        verify(eventPublisher).hospitalClaimCodeIssued(event.capture());
        String code = event.getValue().claimCode();
        assertThat(code).matches("[A-HJ-KM-NP-Z2-9]{4}-[A-HJ-KM-NP-Z2-9]{4}");
        assertThat(event.getValue().email()).isEqualTo("admin@city.in");
        assertThat(event.getValue().reason()).isEqualTo(HospitalClaimCodeIssuedEvent.INITIAL);
        assertThat(event.getValue().expiresAt()).isEqualTo(NOW.plus(HospitalClaimServiceImpl.CODE_TTL).toString());
        // Only the hash is stored, and it matches the emailed code.
        assertThat(hospital.getClaimCodeHash()).doesNotContain(HospitalClaimServiceImpl.normalize(code));
        assertThat(encoder.matches(HospitalClaimServiceImpl.normalize(code), hospital.getClaimCodeHash())).isTrue();
        // The response never carries the code - only where it went.
        assertThat(response.isClaimed()).isFalse();
        assertThat(response.getClaimCodeSentTo()).isEqualTo("ad***@city.in");
        assertThat(response.getClaimCodeExpiresAt()).isEqualTo(NOW.plus(HospitalClaimServiceImpl.CODE_TTL));
    }

    @Test
    void onboard_withoutEmail_createsTheHospitalButSendsNoCode() {
        when(hospitalService.createOnboarded(eq("pro-1"), any(HospitalRequest.class))).thenReturn(created());
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(unclaimed(null)));

        HospitalResponse response = service.onboard("pro-1", HospitalRequest.builder().build());

        assertThat(response.getId()).isEqualTo("h1");
        assertThat(response.getClaimCodeSentTo()).isNull();
        verify(eventPublisher, never()).hospitalClaimCodeIssued(any());
    }

    // ── claim ──────────────────────────────────────────────────────────────────

    @Test
    void claim_correctCode_anyCaseAndDash_bindsTheAccount() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 0);
        when(hospitalRepository.existsByUserId("u7")).thenReturn(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        Hospital bound = unclaimed("admin@city.in");
        bound.setUserId("u7");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Hospital.class)))
                .thenReturn(bound);

        HospitalResponse response = service.claim("u7", new ClaimHospitalRequest(" h1 ", "k7p4-qx2m"));

        assertThat(response.getUserId()).isEqualTo("u7");
        assertThat(response.isClaimed()).isTrue();
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(Hospital.class));
        // Atomic: only while still unclaimed and still holding the checked code.
        assertThat(query.getValue().getQueryObject().toJson()).contains("\"userId\": {\"$exists\": false}").contains("claimCodeHash");
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        Document unset = (Document) update.getValue().getUpdateObject().get("$unset");
        assertThat(set.get("userId")).isEqualTo("u7");
        assertThat(unset.keySet()).contains("claimCodeHash", "claimCodeExpiresAt");
    }

    @Test
    void claim_wrongCode_countsTheAttempt_andSaysHowManyAreLeft() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 1);
        when(hospitalRepository.existsByUserId("u7")).thenReturn(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        Hospital afterInc = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 2);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Hospital.class)))
                .thenReturn(afterInc);

        assertThatThrownBy(() -> service.claim("u7", new ClaimHospitalRequest("h1", "AAAA-AAAA")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Incorrect claim code. 3 attempts left.");
    }

    @Test
    void claim_lastWrongAttempt_locks() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 4);
        when(hospitalRepository.existsByUserId("u7")).thenReturn(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Hospital.class)))
                .thenReturn(withCode("K7P4QX2M", NOW.plusSeconds(86_400), 5));

        assertThatThrownBy(() -> service.claim("u7", new ClaimHospitalRequest("h1", "AAAA-AAAA")))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void claim_afterTheAttemptLimit_isRefusedEvenWithTheRightCode() {
        when(hospitalRepository.existsByUserId("u7")).thenReturn(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(withCode("K7P4QX2M", NOW.plusSeconds(86_400), 5)));

        assertThatThrownBy(() -> service.claim("u7", new ClaimHospitalRequest("h1", "K7P4-QX2M")))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Hospital.class));
    }

    @Test
    void claim_expiredCode_isRefused() {
        when(hospitalRepository.existsByUserId("u7")).thenReturn(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(withCode("K7P4QX2M", NOW.minusSeconds(1), 0)));

        assertThatThrownBy(() -> service.claim("u7", new ClaimHospitalRequest("h1", "K7P4QX2M")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void claim_accountThatAlreadyOwnsAHospital_isRefused() {
        when(hospitalRepository.existsByUserId("u7")).thenReturn(true);

        assertThatThrownBy(() -> service.claim("u7", new ClaimHospitalRequest("h1", "K7P4QX2M")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void claim_alreadyClaimedHospital_isRefused() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 0);
        hospital.setUserId("someone-else");
        when(hospitalRepository.existsByUserId("u7")).thenReturn(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));

        assertThatThrownBy(() -> service.claim("u7", new ClaimHospitalRequest("h1", "K7P4QX2M")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void claim_codeOfWrongLength_isRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> service.claim("u7", new ClaimHospitalRequest("h1", "ABC")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(hospitalRepository, never()).findById(any());
    }

    // ── resend ─────────────────────────────────────────────────────────────────

    @Test
    void resend_byTheOnboardingPro_issuesANewCode() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 3);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        answerFindAndModifyWithSetApplied(hospital);

        HospitalResponse response = service.resendCode("pro-1", Role.PRO, "h1");

        ArgumentCaptor<HospitalClaimCodeIssuedEvent> event = ArgumentCaptor.forClass(HospitalClaimCodeIssuedEvent.class);
        verify(eventPublisher).hospitalClaimCodeIssued(event.capture());
        assertThat(event.getValue().reason()).isEqualTo(HospitalClaimCodeIssuedEvent.RESEND);
        // The old code no longer works and the attempt count is reset.
        assertThat(encoder.matches("K7P4QX2M", hospital.getClaimCodeHash())).isFalse();
        assertThat(hospital.getClaimAttempts()).isZero();
        assertThat(response.getClaimCodeSentTo()).isEqualTo("ad***@city.in");
    }

    @Test
    void resend_byAnotherPro_isForbidden() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(withCode("K7P4QX2M", NOW.plusSeconds(86_400), 0)));

        assertThatThrownBy(() -> service.resendCode("pro-2", Role.PRO, "h1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void resend_byAdmin_isAllowed() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 0);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        answerFindAndModifyWithSetApplied(hospital);

        service.resendCode("admin-1", Role.ADMIN, "h1");

        verify(eventPublisher).hospitalClaimCodeIssued(any());
    }

    @Test
    void resend_tooSoonAfterTheLastCode_isRefused() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 0);
        hospital.setClaimCodeIssuedAt(NOW.minusSeconds(10));
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));

        assertThatThrownBy(() -> service.resendCode("pro-1", Role.PRO, "h1"))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(eventPublisher, never()).hospitalClaimCodeIssued(any());
    }

    @Test
    void resend_forAClaimedHospital_isRefused() {
        Hospital hospital = withCode("K7P4QX2M", NOW.plusSeconds(86_400), 0);
        hospital.setUserId("u7");
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));

        assertThatThrownBy(() -> service.resendCode("pro-1", Role.PRO, "h1"))
                .isInstanceOf(ConflictException.class);
    }

    // ── code format ────────────────────────────────────────────────────────────

    @Test
    void codes_useTheUnambiguousAlphabet_andNormaliseTypedInput() {
        String code = service.generateCode();
        assertThat(code).hasSize(8).matches("[" + HospitalClaimServiceImpl.ALPHABET + "]+");
        assertThat(HospitalClaimServiceImpl.format("K7P4QX2M")).isEqualTo("K7P4-QX2M");
        assertThat(HospitalClaimServiceImpl.normalize(" k7p4 - qx2m ")).isEqualTo("K7P4QX2M");
    }
}
