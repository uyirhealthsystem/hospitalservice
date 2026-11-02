package com.uyir.hospital.service.impl;

import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.model.AppointmentTokenClaim;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.enums.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

// Hands out OPD token numbers per hospital, per doctor, per local day. A patient gets the lowest
// free number, so a token freed by a cancellation (or a move to another day) goes to the next patient.
// When the hospital has set a limit, numbers stay within 1..limit and booking stops once all are taken.
//
// Each token in use is an AppointmentTokenClaim whose _id encodes the number, so the insert itself is
// the lock: two concurrent bookings racing for the same number cannot both succeed.
@Component
@RequiredArgsConstructor
public class AppointmentTokenGenerator {

    // Same zone the analytics day buckets use; the "day" a token belongs to is the hospital's local day.
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final MongoTemplate mongoTemplate;

    // limit == null means unlimited. Throws ConflictException when every token for the day is taken.
    public int claim(String hospitalId, String doctorId, Instant appointmentDateTime, Integer limit) {
        LocalDate day = localDay(appointmentDateTime);
        if (limit != null && limit <= 0) {
            throw new ConflictException("Doctor '" + doctorId + "' is not taking appointments on " + day);
        }

        Set<Integer> taken = takenTokens(hospitalId, doctorId, day);
        if (limit != null && taken.size() >= limit) {
            throw tokensFull(doctorId, day, limit);
        }

        Instant now = Instant.now();
        for (int token = 1; limit == null || token <= limit; token++) {
            if (taken.contains(token)) {
                continue;
            }
            try {
                mongoTemplate.insert(AppointmentTokenClaim.builder()
                        .id(AppointmentTokenClaim.idFor(hospitalId, doctorId, day.toString(), token))
                        .hospitalId(hospitalId)
                        .doctorId(doctorId)
                        .day(day.toString())
                        .tokenNumber(token)
                        .createdAt(now)
                        .build());
                return token;
            } catch (DuplicateKeyException raced) {
                // Another booking took this number between our read and insert; try the next one.
            }
        }
        throw tokensFull(doctorId, day, limit);
    }

    // Frees a token so the next booking for that day can take it. No-op if it was never claimed.
    public void release(String hospitalId, String doctorId, Instant appointmentDateTime, Integer tokenNumber) {
        if (tokenNumber == null || appointmentDateTime == null) {
            return;
        }
        String id = AppointmentTokenClaim.idFor(hospitalId, doctorId, localDay(appointmentDateTime).toString(), tokenNumber);
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), AppointmentTokenClaim.class);
    }

    // Token numbers in use for the day. Also counts bookings made before token claims existed
    // (they only carry tokenNumber), so their numbers are never handed out twice.
    public Set<Integer> takenTokens(String hospitalId, String doctorId, LocalDate day) {
        Set<Integer> taken = new TreeSet<>();
        mongoTemplate.find(
                        Query.query(Criteria.where("hospitalId").is(hospitalId)
                                .and("doctorId").is(doctorId)
                                .and("day").is(day.toString())),
                        AppointmentTokenClaim.class)
                .forEach(claim -> taken.add(claim.getTokenNumber()));

        Instant from = day.atStartOfDay(ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        Query legacy = Query.query(Criteria.where("hospitalId").is(hospitalId)
                .and("doctorId").is(doctorId)
                .and("appointmentDateTime").gte(from).lt(to)
                .and("status").ne(AppointmentStatus.CANCELLED)
                .and("tokenNumber").ne(null));
        legacy.fields().include("tokenNumber");
        mongoTemplate.find(legacy, DoctorAppointmentBooking.class)
                .forEach(booking -> taken.add(booking.getTokenNumber()));
        return taken;
    }

    public static LocalDate localDay(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    public static boolean sameDay(Instant a, Instant b) {
        return localDay(a).equals(localDay(b));
    }

    private static ConflictException tokensFull(String doctorId, LocalDate day, Integer limit) {
        return new ConflictException("All " + limit + " appointment tokens for doctor '" + doctorId + "' on "
                + day + " are booked");
    }
}
