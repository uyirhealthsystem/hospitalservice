package com.uyir.hospital.service.impl;

import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.embedded.FollowUpInstruction;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

// Atomic updates to an appointment's follow-up instruction. Each is a single conditional write, so a
// patient booking the follow-up and the hospital editing the instruction can't both win, and every
// replica's reminder job sends each reminder once.
@Component
@RequiredArgsConstructor
public class FollowUpStore {

    private static final String BOOKING_ID = "followUp.followUpBookingId";

    private final MongoTemplate mongoTemplate;

    // Links a new FOLLOW_UP booking to the appointment that asked for it. False if one is already linked.
    public boolean link(String previousAppointmentId, String followUpBookingId) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(previousAppointmentId)
                        .and("followUp").ne(null)
                        .and(BOOKING_ID).is(null)),
                new Update().set(BOOKING_ID, followUpBookingId),
                DoctorAppointmentBooking.class).getModifiedCount() == 1;
    }

    // Frees the instruction again (the follow-up booking was cancelled or failed to save).
    public void unlink(String previousAppointmentId, String followUpBookingId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(previousAppointmentId).and(BOOKING_ID).is(followUpBookingId)),
                new Update().set(BOOKING_ID, null),
                DoctorAppointmentBooking.class);
    }

    // Sets, replaces (followUp != null) or removes (null) the instruction unless a follow-up is already
    // booked against it. False if one is.
    public boolean replaceIfNotBooked(String appointmentId, FollowUpInstruction followUp) {
        Update update = followUp != null ? new Update().set("followUp", followUp) : new Update().unset("followUp");
        update.set("updatedAt", Instant.now());
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(appointmentId).and(BOOKING_ID).is(null)),
                update,
                DoctorAppointmentBooking.class).getMatchedCount() == 1;
    }

    // Marks a reminder as sent. True only for the one caller that flipped it, which then sends it.
    public boolean claimReminder(String appointmentId, String sentAtField) {
        String field = "followUp." + sentAtField;
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(appointmentId).and(field).is(null).and(BOOKING_ID).is(null)),
                new Update().set(field, Instant.now()),
                DoctorAppointmentBooking.class).getModifiedCount() == 1;
    }
}
