package com.uyir.hospital.event;

// `data` of the HospitalClaimCodeIssued (v1) event on hospital.events. notification-service emails the
// hospital its id and the one-time claim code, so the hospital's own HOSPITAL account can link itself to the
// hospital a PRO onboarded (POST /api/v1/hospital/claim). reason: INITIAL (the PRO just created it) or RESEND.
// expiresAt is ISO-8601 (UTC). This is the only place the plain code exists - the hospital stores a hash.
public record HospitalClaimCodeIssuedEvent(
        String hospitalId,
        String hospitalName,
        String email,
        String claimCode,
        String expiresAt,
        String reason) {

    public static final String EVENT_TYPE = "HospitalClaimCodeIssued";
    public static final int EVENT_VERSION = 1;
    public static final String INITIAL = "INITIAL";
    public static final String RESEND = "RESEND";
}
