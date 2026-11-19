package com.uyir.hospital.event;

import java.time.Instant;

// `data` of the EmergencyServicesExpiring (v1) event: the hospital's emergency mode switches itself off at
// `emergencyOnUntil` unless the hospital turns it on again (PATCH /{id}/emergency-services/toggle). Sent once
// per lease by EmergencyServicesExpiryJob; every pod passes it to that hospital's live WebSocket connections.
public record EmergencyServicesExpiringEvent(
        String hospitalId,
        String hospitalName,
        Instant emergencyOnUntil,
        Instant occurredAt) {

    public static final String EVENT_TYPE = "EmergencyServicesExpiring";
    public static final int EVENT_VERSION = 1;

    // The `type` of the WebSocket message, next to the booking update types.
    public static final String LIVE_MESSAGE_TYPE = "EMERGENCY_SERVICES_EXPIRING";
}
