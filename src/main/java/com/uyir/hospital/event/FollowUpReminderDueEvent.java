package com.uyir.hospital.event;

// `data` of the FollowUpReminderDue (v1) event on hospital.events. notification-service emails it to
// patientEmail. reminderType: UPCOMING (sent up to 2 days before dueDate) or DUE_TODAY. Dates are
// yyyy-MM-dd (IST); the follow-up can be booked for any date in [bookableFrom, bookableUntil].
public record FollowUpReminderDueEvent(
        String appointmentId,
        String reminderType,
        String patientId,
        String patientName,
        String patientEmail,
        String hospitalId,
        String hospitalName,
        String hospitalPhone,
        String doctorId,
        String doctorName,
        String dueDate,
        String bookableFrom,
        String bookableUntil,
        String instructions) {

    public static final String EVENT_TYPE = "FollowUpReminderDue";
    public static final int EVENT_VERSION = 1;

    public static final String UPCOMING = "UPCOMING";
    public static final String DUE_TODAY = "DUE_TODAY";
}
