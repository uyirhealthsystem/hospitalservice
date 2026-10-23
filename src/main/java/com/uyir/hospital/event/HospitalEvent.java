package com.uyir.hospital.event;

import java.time.Instant;

// Envelope for every domain event on the hospital.events topic - same shape authservice uses on
// auth.events, which notification-service validates. It is a contract: bump eventVersion on a
// breaking change to `data` rather than changing v1.
public record HospitalEvent<T>(String eventType, int eventVersion, Instant occurredAt, T data) {

    public static <T> HospitalEvent<T> of(String eventType, int eventVersion, T data) {
        return new HospitalEvent<>(eventType, eventVersion, Instant.now(), data);
    }
}
