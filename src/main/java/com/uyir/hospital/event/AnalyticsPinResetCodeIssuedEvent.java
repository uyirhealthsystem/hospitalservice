package com.uyir.hospital.event;

// `data` of the AnalyticsPinResetCodeIssued (v1) event on hospital.events. notification-service emails the
// hospital a one-time code to reset a forgotten analytics PIN (POST /api/v1/hospital/analytics-pin/reset/confirm).
// expiresAt is ISO-8601 (UTC). This is the only place the plain code exists - the hospital stores a hash.
public record AnalyticsPinResetCodeIssuedEvent(
        String hospitalId,
        String hospitalName,
        String email,
        String resetCode,
        String expiresAt) {

    public static final String EVENT_TYPE = "AnalyticsPinResetCodeIssued";
    public static final int EVENT_VERSION = 1;
}
