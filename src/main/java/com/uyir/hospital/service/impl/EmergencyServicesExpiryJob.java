package com.uyir.hospital.service.impl;

import com.uyir.hospital.event.EmergencyServicesExpiringEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.service.EmergencyServicesProperties;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Emergency mode switches itself off: a hospital that turned it on more than hospital.emergency.on-duration
// (6h) ago and hasn't turned it on again goes back to handlesEmergencies=false. SOS and emergency booking
// already treat it as off the moment the lease ends (EmergencyServices.onAt); this job makes the stored flag
// match within a minute and warns the hospital's dashboards before it happens.
//
// Runs on every pod, no lock needed: each step is a single conditional Mongo update, so running it twice -
// or on ten pods at once - changes nothing more, and a lease renewed in between no longer matches.
@Component
@RequiredArgsConstructor
public class EmergencyServicesExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(EmergencyServicesExpiryJob.class);

    static final String HANDLES = "emergencyServices.handlesEmergencies";
    static final String ON_UNTIL = "emergencyServices.emergencyOnUntil";
    static final String WARNING_SENT_AT = "emergencyServices.expiryWarningSentAt";

    private final MongoTemplate mongoTemplate;
    private final HospitalEventPublisher eventPublisher;
    private final EmergencyServicesProperties properties;

    @Scheduled(fixedDelay = 60, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void run() {
        Instant now = Instant.now();
        try {
            grantMissingLeases(now);
            warnExpiringSoon(now);
            switchOffExpired(now);
        } catch (RuntimeException e) {
            log.error("Emergency mode expiry run failed; the next run retries in a minute", e);
        }
    }

    // Hospitals that were on before leases existed (or set on by any path that didn't stamp one) get a full lease
    // from now - nobody drops out of SOS by surprise on the day this ships.
    long grantMissingLeases(Instant now) {
        long granted = mongoTemplate.updateMulti(
                Query.query(Criteria.where(HANDLES).is(true).and(ON_UNTIL).is(null)),
                new Update().set(ON_UNTIL, now.plus(properties.onDuration())).unset(WARNING_SENT_AT),
                Hospital.class).getModifiedCount();
        if (granted > 0) {
            log.info("Emergency mode: gave {} hospital(s) without a lease a {} lease", granted, properties.onDuration());
        }
        return granted;
    }

    // One warning per lease. The claim (set expiryWarningSentAt only if still unset for this exact lease) is
    // atomic, so exactly one pod publishes it; Kafka then delivers it to every pod's connections.
    int warnExpiringSoon(Instant now) {
        Query due = Query.query(Criteria.where(HANDLES).is(true)
                .and(ON_UNTIL).gt(now).lte(now.plus(properties.expiryWarningBefore()))
                .and(WARNING_SENT_AT).is(null));
        due.fields().include("hospitalName").include(ON_UNTIL);
        List<Hospital> expiring = mongoTemplate.find(due, Hospital.class);

        int warned = 0;
        for (Hospital hospital : expiring) {
            Instant onUntil = hospital.getEmergencyServices().getEmergencyOnUntil();
            boolean claimed = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(hospital.getId())
                            .and(HANDLES).is(true).and(ON_UNTIL).is(onUntil).and(WARNING_SENT_AT).is(null)),
                    new Update().set(WARNING_SENT_AT, now),
                    Hospital.class).getModifiedCount() == 1;
            if (claimed) {
                eventPublisher.emergencyServicesExpiring(new EmergencyServicesExpiringEvent(
                        hospital.getId(), hospital.getHospitalName(), onUntil, now));
                warned++;
            }
        }
        return warned;
    }

    // Lease over -> off. Only leases that have already ended match, so a hospital that just extended is untouched.
    long switchOffExpired(Instant now) {
        long switchedOff = mongoTemplate.updateMulti(
                Query.query(Criteria.where(HANDLES).is(true).and(ON_UNTIL).lte(now)),
                new Update().set(HANDLES, false).unset(ON_UNTIL).unset(WARNING_SENT_AT).set("updatedAt", now),
                Hospital.class).getModifiedCount();
        if (switchedOff > 0) {
            log.info("Emergency mode: switched off {} hospital(s) whose {} lease ended", switchedOff, properties.onDuration());
        }
        return switchedOff;
    }
}
