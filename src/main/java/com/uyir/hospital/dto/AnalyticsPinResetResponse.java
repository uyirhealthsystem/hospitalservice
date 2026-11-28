package com.uyir.hospital.dto;

import java.time.Instant;

// Where the reset code went (masked, e.g. "ad***@hospital.in") and when it stops working.
public record AnalyticsPinResetResponse(String codeSentTo, Instant expiresAt) {
}
