package com.uyir.hospital.dto;

import java.time.Instant;

// Whether the hospital has an analytics PIN, when it last changed, and - only while locked after too many
// wrong PINs - when it unlocks (null otherwise). The PIN itself is never returned.
public record AnalyticsPinStatusResponse(boolean pinSet, Instant pinUpdatedAt, Instant lockedUntil) {
}
