package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.AnalyticsPinResetResponse;
import com.uyir.hospital.dto.AnalyticsPinStatusResponse;
import com.uyir.hospital.event.AnalyticsPinResetCodeIssuedEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.exception.AnalyticsPinException;
import com.uyir.hospital.exception.AnalyticsPinException.Code;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.exception.TooManyAttemptsException;
import com.uyir.hospital.mapper.HospitalMapper;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.AnalyticsPinService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

// The PIN lives on the Hospital document as a BCrypt hash. Every analytics call sends the PIN and is
// checked here - no unlock token or session. Counters change with atomic Mongo updates so pods can't
// lose a wrong attempt to a race.
@Service
public class AnalyticsPinServiceImpl implements AnalyticsPinService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPinServiceImpl.class);

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final int RESET_CODE_LENGTH = 6;
    static final Duration RESET_CODE_TTL = Duration.ofMinutes(15);
    static final Duration RESET_RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final HospitalRepository hospitalRepository;
    private final MongoTemplate mongoTemplate;
    private final HospitalEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom random;
    private final PasswordEncoder encoder;

    @Autowired
    public AnalyticsPinServiceImpl(
            HospitalRepository hospitalRepository, MongoTemplate mongoTemplate, HospitalEventPublisher eventPublisher) {
        this(hospitalRepository, mongoTemplate, eventPublisher,
                Clock.systemUTC(), new SecureRandom(), new BCryptPasswordEncoder());
    }

    AnalyticsPinServiceImpl(
            HospitalRepository hospitalRepository,
            MongoTemplate mongoTemplate,
            HospitalEventPublisher eventPublisher,
            Clock clock,
            SecureRandom random,
            PasswordEncoder encoder) {
        this.hospitalRepository = hospitalRepository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.random = random;
        this.encoder = encoder;
    }

    @Override
    public AnalyticsPinStatusResponse getStatus(String hospitalId) {
        return status(find(hospitalId));
    }

    @Override
    public AnalyticsPinStatusResponse setPin(String hospitalId, String pin) {
        String clean = validateNewPin(pin);
        find(hospitalId);
        Instant now = now();
        // Only while no PIN exists, so two first-time requests can't overwrite each other.
        Query noPinYet = Query.query(Criteria.where("_id").is(hospitalId).and("analyticsPinHash").exists(false));
        Hospital updated = mongoTemplate.findAndModify(noPinYet, newPinUpdate(clean, now),
                FindAndModifyOptions.options().returnNew(true), Hospital.class);
        if (updated == null) {
            throw new AnalyticsPinException(Code.ANALYTICS_PIN_ALREADY_SET,
                    "An analytics PIN is already set. Change it, or reset it if you forgot it.");
        }
        log.info("Analytics PIN set for hospital '{}'", hospitalId);
        return status(updated);
    }

    @Override
    public AnalyticsPinStatusResponse changePin(String hospitalId, String currentPin, String newPin) {
        // Format first, so a typo in the new PIN doesn't cost an attempt.
        String clean = validateNewPin(newPin);
        verify(hospitalId, currentPin);
        if (clean.equals(currentPin.trim())) {
            throw new IllegalArgumentException("The new PIN must be different from the current PIN");
        }
        Hospital updated = mongoTemplate.findAndModify(Query.query(Criteria.where("_id").is(hospitalId)),
                newPinUpdate(clean, now()), FindAndModifyOptions.options().returnNew(true), Hospital.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Hospital with id '" + hospitalId + "' not found");
        }
        log.info("Analytics PIN changed for hospital '{}'", hospitalId);
        return status(updated);
    }

    @Override
    public void verify(String hospitalId, String pin) {
        Hospital hospital = find(hospitalId);
        if (hospital.getAnalyticsPinHash() == null) {
            throw new AnalyticsPinException(Code.ANALYTICS_PIN_NOT_SET,
                    "Set an analytics PIN before viewing your hospital's analytics");
        }
        if (pin == null || pin.isBlank()) {
            throw new AnalyticsPinException(Code.ANALYTICS_PIN_REQUIRED, "Enter your analytics PIN");
        }
        Instant lockedUntil = hospital.getAnalyticsPinLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now())) {
            throw locked(lockedUntil);
        }
        if (!encoder.matches(pin.trim(), hospital.getAnalyticsPinHash())) {
            recordWrongPin(hospital);
        }
        if (hospital.getAnalyticsPinFailedAttempts() > 0) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(hospitalId)),
                    new Update().set("analyticsPinFailedAttempts", 0), Hospital.class);
        }
    }

    @Override
    public AnalyticsPinResetResponse requestReset(String hospitalId) {
        Hospital hospital = find(hospitalId);
        if (hospital.getAnalyticsPinHash() == null) {
            throw new ConflictException("No analytics PIN is set yet - set one instead of resetting it");
        }
        String email = email(hospital);
        if (email == null) {
            throw new ConflictException("Your hospital has no contact email. Add one to your profile to reset the PIN.");
        }
        Instant lastSent = hospital.getAnalyticsPinResetCodeIssuedAt();
        if (lastSent != null && lastSent.plus(RESET_RESEND_COOLDOWN).isAfter(now())) {
            throw new TooManyAttemptsException("A reset code was just sent. Wait a minute before asking for another.");
        }
        String code = generateResetCode();
        Instant issuedAt = now();
        Instant expiresAt = issuedAt.plus(RESET_CODE_TTL);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(hospitalId)), new Update()
                        .set("analyticsPinResetCodeHash", encoder.encode(code))
                        .set("analyticsPinResetCodeIssuedAt", issuedAt)
                        .set("analyticsPinResetCodeExpiresAt", expiresAt)
                        .set("analyticsPinResetAttempts", 0),
                Hospital.class);
        eventPublisher.analyticsPinResetCodeIssued(new AnalyticsPinResetCodeIssuedEvent(
                hospitalId, hospital.getHospitalName(), email, code, expiresAt.toString()));
        log.info("Analytics PIN reset code issued for hospital '{}', expires {}", hospitalId, expiresAt);
        return new AnalyticsPinResetResponse(HospitalMapper.maskEmail(email), expiresAt);
    }

    @Override
    public AnalyticsPinStatusResponse confirmReset(String hospitalId, String resetCode, String newPin) {
        String clean = validateNewPin(newPin);
        Hospital hospital = find(hospitalId);
        String codeHash = hospital.getAnalyticsPinResetCodeHash();
        if (codeHash == null) {
            throw new ConflictException("No reset code is active. Ask for a new one.");
        }
        if (hospital.getAnalyticsPinResetAttempts() >= MAX_ATTEMPTS) {
            throw new TooManyAttemptsException("Too many wrong reset codes. Ask for a new one.");
        }
        Instant expiresAt = hospital.getAnalyticsPinResetCodeExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(now())) {
            throw new ConflictException("This reset code has expired. Ask for a new one.");
        }
        String typed = resetCode == null ? "" : resetCode.replaceAll("\\D", "");
        if (!encoder.matches(typed, codeHash)) {
            Hospital after = mongoTemplate.findAndModify(Query.query(Criteria.where("_id").is(hospitalId)),
                    new Update().inc("analyticsPinResetAttempts", 1),
                    FindAndModifyOptions.options().returnNew(true), Hospital.class);
            int attempts = after == null ? hospital.getAnalyticsPinResetAttempts() + 1 : after.getAnalyticsPinResetAttempts();
            int left = MAX_ATTEMPTS - attempts;
            if (left <= 0) {
                throw new TooManyAttemptsException("Too many wrong reset codes. Ask for a new one.");
            }
            throw new IllegalArgumentException("Incorrect reset code. " + attemptsLeft(left));
        }
        // Single use: only while the code that was checked is still the active one.
        Query withThisCode = Query.query(Criteria.where("_id").is(hospitalId).and("analyticsPinResetCodeHash").is(codeHash));
        Update update = newPinUpdate(clean, now())
                .unset("analyticsPinResetCodeHash")
                .unset("analyticsPinResetCodeIssuedAt")
                .unset("analyticsPinResetCodeExpiresAt")
                .set("analyticsPinResetAttempts", 0);
        Hospital updated = mongoTemplate.findAndModify(withThisCode, update,
                FindAndModifyOptions.options().returnNew(true), Hospital.class);
        if (updated == null) {
            throw new ConflictException("This reset code was already used. Ask for a new one.");
        }
        log.info("Analytics PIN reset for hospital '{}'", hospitalId);
        return status(updated);
    }

    // Counts the wrong PIN; at the limit, locks the PIN and starts the count again. Always throws.
    private void recordWrongPin(Hospital hospital) {
        Query byId = Query.query(Criteria.where("_id").is(hospital.getId()));
        Hospital after = mongoTemplate.findAndModify(byId, new Update().inc("analyticsPinFailedAttempts", 1),
                FindAndModifyOptions.options().returnNew(true), Hospital.class);
        int attempts = after == null ? hospital.getAnalyticsPinFailedAttempts() + 1 : after.getAnalyticsPinFailedAttempts();
        int left = MAX_ATTEMPTS - attempts;
        if (left <= 0) {
            Instant lockedUntil = now().plus(LOCK_DURATION);
            mongoTemplate.updateFirst(byId, new Update()
                    .set("analyticsPinLockedUntil", lockedUntil)
                    .set("analyticsPinFailedAttempts", 0), Hospital.class);
            log.warn("Analytics PIN for hospital '{}' locked until {} after {} wrong attempts",
                    hospital.getId(), lockedUntil, MAX_ATTEMPTS);
            throw locked(lockedUntil);
        }
        throw new AnalyticsPinException(Code.ANALYTICS_PIN_INCORRECT, "Incorrect PIN. " + attemptsLeft(left));
    }

    private Update newPinUpdate(String pin, Instant now) {
        return new Update()
                .set("analyticsPinHash", encoder.encode(pin))
                .set("analyticsPinUpdatedAt", now)
                .set("analyticsPinFailedAttempts", 0)
                .unset("analyticsPinLockedUntil")
                .set("updatedAt", now);
    }

    private AnalyticsPinException locked(Instant lockedUntil) {
        long minutes = Math.max(1, (Duration.between(now(), lockedUntil).getSeconds() + 59) / 60);
        return new AnalyticsPinException(Code.ANALYTICS_PIN_LOCKED, "Too many wrong PINs. Try again in "
                + minutes + " minute" + (minutes == 1 ? "" : "s") + ", or reset your PIN.");
    }

    private AnalyticsPinStatusResponse status(Hospital hospital) {
        Instant lockedUntil = hospital.getAnalyticsPinLockedUntil();
        return new AnalyticsPinStatusResponse(
                hospital.getAnalyticsPinHash() != null,
                hospital.getAnalyticsPinUpdatedAt(),
                lockedUntil != null && lockedUntil.isAfter(now()) ? lockedUntil : null);
    }

    // 4-6 digits; rejects all-same (0000) and straight runs (1234, 654321) - the first PINs anyone tries.
    static String validateNewPin(String pin) {
        String clean = pin == null ? "" : pin.trim();
        if (!clean.matches("\\d{4,6}")) {
            throw new IllegalArgumentException("The PIN must be 4 to 6 digits");
        }
        if (isRepeatedOrSequential(clean)) {
            throw new IllegalArgumentException("Choose a PIN that is harder to guess - not repeated (1111) or in order (1234)");
        }
        return clean;
    }

    static boolean isRepeatedOrSequential(String digits) {
        boolean same = true;
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < digits.length(); i++) {
            int step = digits.charAt(i) - digits.charAt(i - 1);
            same &= step == 0;
            up &= step == 1;
            down &= step == -1;
        }
        return same || up || down;
    }

    String generateResetCode() {
        StringBuilder code = new StringBuilder(RESET_CODE_LENGTH);
        for (int i = 0; i < RESET_CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private static String attemptsLeft(int left) {
        return left + " attempt" + (left == 1 ? "" : "s") + " left.";
    }

    private Hospital find(String id) {
        return hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital with id '" + id + "' not found"));
    }

    private static String email(Hospital hospital) {
        String email = hospital.getContactDetails() == null ? null : hospital.getContactDetails().getEmail();
        return email == null || email.isBlank() ? null : email.trim();
    }

    private Instant now() {
        return clock.instant();
    }
}
