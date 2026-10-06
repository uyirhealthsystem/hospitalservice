package com.uyir.hospital.service.impl;

import com.uyir.hospital.model.AppointmentTokenCounter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

// Hands out OPD token numbers: 1, 2, 3, ... per doctor per local day, in booking order.
// The increment is a single atomic findAndModify, so concurrent bookings never share a token.
// Tokens of cancelled appointments are not reused.
@Component
@RequiredArgsConstructor
public class AppointmentTokenGenerator {

    // Same zone the analytics day buckets use; the "day" a token belongs to is the hospital's local day.
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final MongoTemplate mongoTemplate;

    public int nextToken(String doctorId, Instant appointmentDateTime) {
        LocalDate day = LocalDate.ofInstant(appointmentDateTime, ZONE);
        AppointmentTokenCounter counter = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(doctorId + ":" + day)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                AppointmentTokenCounter.class);
        return counter.getSeq();
    }

    public static boolean sameDay(Instant a, Instant b) {
        return LocalDate.ofInstant(a, ZONE).equals(LocalDate.ofInstant(b, ZONE));
    }
}
