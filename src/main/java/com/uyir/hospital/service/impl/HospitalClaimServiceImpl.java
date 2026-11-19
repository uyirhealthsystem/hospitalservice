package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.ClaimHospitalRequest;
import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.event.HospitalClaimCodeIssuedEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.exception.TooManyAttemptsException;
import com.uyir.hospital.mapper.HospitalMapper;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalClaimService;
import com.uyir.hospital.service.HospitalService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class HospitalClaimServiceImpl implements HospitalClaimService {

    private static final Logger log = LoggerFactory.getLogger(HospitalClaimServiceImpl.class);

    // No 0/O, 1/I/L - the code is read from an email and typed on a phone.
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;
    static final Duration CODE_TTL = Duration.ofDays(7);
    static final int MAX_ATTEMPTS = 5;
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final HospitalService hospitalService;
    private final HospitalRepository hospitalRepository;
    private final MongoTemplate mongoTemplate;
    private final HospitalEventPublisher eventPublisher;
    private final HospitalMapper hospitalMapper;
    private final Clock clock;
    private final SecureRandom random;
    private final PasswordEncoder encoder;

    @Autowired
    public HospitalClaimServiceImpl(
            HospitalService hospitalService,
            HospitalRepository hospitalRepository,
            MongoTemplate mongoTemplate,
            HospitalEventPublisher eventPublisher,
            HospitalMapper hospitalMapper) {
        this(hospitalService, hospitalRepository, mongoTemplate, eventPublisher, hospitalMapper,
                Clock.systemUTC(), new SecureRandom(), new BCryptPasswordEncoder());
    }

    HospitalClaimServiceImpl(
            HospitalService hospitalService,
            HospitalRepository hospitalRepository,
            MongoTemplate mongoTemplate,
            HospitalEventPublisher eventPublisher,
            HospitalMapper hospitalMapper,
            Clock clock,
            SecureRandom random,
            PasswordEncoder encoder) {
        this.hospitalService = hospitalService;
        this.hospitalRepository = hospitalRepository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
        this.hospitalMapper = hospitalMapper;
        this.clock = clock;
        this.random = random;
        this.encoder = encoder;
    }

    @Override
    public HospitalResponse onboard(String proUserId, HospitalRequest request) {
        HospitalResponse created = hospitalService.createOnboarded(proUserId, request);
        Hospital hospital = find(created.getId());
        if (email(hospital) == null) {
            // contactDetails.email is optional in HospitalRequest. The hospital still exists; an admin adds the
            // email and resends. The response shows claimCodeSentTo = null so the PRO app can say so.
            log.warn("Hospital '{}' onboarded by PRO '{}' has no contact email - no claim code sent", hospital.getId(), proUserId);
            return created;
        }
        return issueCode(hospital, HospitalClaimCodeIssuedEvent.INITIAL);
    }

    @Override
    public HospitalResponse resendCode(String callerUserId, Role callerRole, String hospitalId) {
        Hospital hospital = find(hospitalId);
        boolean admin = callerRole == Role.ADMIN || callerRole == Role.SUPER_ADMIN;
        boolean onboardingPro = callerRole == Role.PRO && callerUserId != null && callerUserId.equals(hospital.getOnboardedByProUserId());
        if (!admin && !onboardingPro) {
            throw new ForbiddenException("Only the PRO who onboarded this hospital or an admin can resend its claim code");
        }
        if (hospital.getUserId() != null) {
            throw new ConflictException("This hospital is already linked to its account");
        }
        if (email(hospital) == null) {
            throw new ConflictException("This hospital has no contact email. Add one before resending the claim code.");
        }
        Instant lastSent = hospital.getClaimCodeIssuedAt();
        if (lastSent != null && lastSent.plus(RESEND_COOLDOWN).isAfter(now())) {
            throw new TooManyAttemptsException("A claim code was just sent. Wait a minute before sending another.");
        }
        return issueCode(hospital, HospitalClaimCodeIssuedEvent.RESEND);
    }

    @Override
    public HospitalResponse claim(String userId, ClaimHospitalRequest request) {
        String code = normalize(request.claimCode());
        if (code.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("The claim code has " + CODE_LENGTH + " letters and numbers - check the email");
        }
        if (hospitalRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("This account is already linked to a hospital");
        }
        Hospital hospital = hospitalRepository.findById(request.hospitalId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("No hospital found with this ID - check the ID in the email"));
        if (hospital.getUserId() != null) {
            throw new ConflictException("This hospital is already linked to an account");
        }
        if (hospital.getClaimCodeHash() == null) {
            throw new ConflictException("This hospital has no active claim code. Ask your PRO to send a new one.");
        }
        if (hospital.getClaimAttempts() >= MAX_ATTEMPTS) {
            throw new TooManyAttemptsException("Too many wrong codes. Ask your PRO to send a new claim code.");
        }
        if (hospital.getClaimCodeExpiresAt() == null || !hospital.getClaimCodeExpiresAt().isAfter(now())) {
            throw new ConflictException("This claim code has expired. Ask your PRO to send a new one.");
        }
        if (!encoder.matches(code, hospital.getClaimCodeHash())) {
            int attempts = recordWrongAttempt(hospital);
            int left = MAX_ATTEMPTS - attempts;
            if (left <= 0) {
                throw new TooManyAttemptsException("Too many wrong codes. Ask your PRO to send a new claim code.");
            }
            throw new IllegalArgumentException("Incorrect claim code. " + left + " attempt" + (left == 1 ? "" : "s") + " left.");
        }

        // Bind atomically: only while still unclaimed and still holding the code that was checked, so two
        // accounts racing with the same code can't both win. The unique userId index stops one account
        // claiming two hospitals at once (DuplicateKeyException -> 409).
        Query unclaimedWithThisCode = Query.query(Criteria.where("_id").is(hospital.getId())
                .and("userId").exists(false)
                .and("claimCodeHash").is(hospital.getClaimCodeHash()));
        Update bind = new Update()
                .set("userId", userId)
                .set("claimedAt", now())
                .set("updatedAt", now())
                .set("claimAttempts", 0)
                .unset("claimCodeHash")
                .unset("claimCodeIssuedAt")
                .unset("claimCodeExpiresAt");
        Hospital claimed = mongoTemplate.findAndModify(unclaimedWithThisCode, bind,
                FindAndModifyOptions.options().returnNew(true), Hospital.class);
        if (claimed == null) {
            throw new ConflictException("This hospital is already linked to an account");
        }
        log.info("Hospital '{}' claimed by user '{}'", claimed.getId(), userId);
        return hospitalMapper.toResponse(claimed);
    }

    // New code replaces any previous one (and resets the wrong-attempt count), then the event asks
    // notification-service to email it. Only the hash is stored.
    private HospitalResponse issueCode(Hospital hospital, String reason) {
        String code = generateCode();
        Instant issuedAt = now();
        Instant expiresAt = issuedAt.plus(CODE_TTL);
        Query unclaimed = Query.query(Criteria.where("_id").is(hospital.getId()).and("userId").exists(false));
        Update update = new Update()
                .set("claimCodeHash", encoder.encode(code))
                .set("claimCodeIssuedAt", issuedAt)
                .set("claimCodeExpiresAt", expiresAt)
                .set("claimAttempts", 0)
                .set("updatedAt", issuedAt);
        Hospital updated = mongoTemplate.findAndModify(unclaimed, update,
                FindAndModifyOptions.options().returnNew(true), Hospital.class);
        if (updated == null) {
            throw new ConflictException("This hospital is already linked to its account");
        }
        eventPublisher.hospitalClaimCodeIssued(new HospitalClaimCodeIssuedEvent(
                updated.getId(),
                updated.getHospitalName(),
                email(updated),
                format(code),
                expiresAt.toString(),
                reason));
        log.info("Claim code ({}) issued for hospital '{}', expires {}", reason, updated.getId(), expiresAt);
        return hospitalMapper.toResponse(updated);
    }

    private int recordWrongAttempt(Hospital hospital) {
        Hospital after = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(hospital.getId())),
                new Update().inc("claimAttempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                Hospital.class);
        return after == null ? hospital.getClaimAttempts() + 1 : after.getClaimAttempts();
    }

    private Hospital find(String id) {
        return hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital with id '" + id + "' not found"));
    }

    private static String email(Hospital hospital) {
        String email = hospital.getContactDetails() == null ? null : hospital.getContactDetails().getEmail();
        return email == null || email.isBlank() ? null : email.trim();
    }

    String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    // "K7P4QX2M" -> "K7P4-QX2M" for the email; typed codes are normalised back.
    static String format(String code) {
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    static String normalize(String typed) {
        return typed == null ? "" : typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private Instant now() {
        return clock.instant();
    }
}
