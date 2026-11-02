package com.uyir.hospital.model.embedded;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// The hospital's "come back for a follow-up" instruction on a COMPLETED appointment.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpInstruction {

    // The follow-up can be booked for a date from 3 days before the due date to 7 days after it.
    public static final int BOOKABLE_DAYS_BEFORE = 3;
    public static final int BOOKABLE_DAYS_AFTER = 7;

    public static java.time.LocalDate bookableFrom(java.time.LocalDate dueDate) {
        return dueDate.minusDays(BOOKABLE_DAYS_BEFORE);
    }

    public static java.time.LocalDate bookableUntil(java.time.LocalDate dueDate) {
        return dueDate.plusDays(BOOKABLE_DAYS_AFTER);
    }

    // yyyy-MM-dd, Asia/Kolkata day
    private String dueDate;
    private String instructions;

    // Hospital id that set it
    private String setBy;
    private Instant setAt;

    // The FOLLOW_UP appointment booked against this instruction; null until the patient books one
    // (and again if that booking is cancelled). Once set, the instruction can no longer be changed.
    private String followUpBookingId;

    // Reminder emails already published, so every replica sends each one only once.
    private Instant upcomingReminderSentAt;
    private Instant dueTodayReminderSentAt;
}
