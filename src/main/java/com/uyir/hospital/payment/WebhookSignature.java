package com.uyir.hospital.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Verifies payment-service callbacks (same scheme as the ambulance module):
//   X-Payment-Timestamp: 1727222400                                    (epoch seconds)
//   X-Payment-Signature: hex(HMAC-SHA256(secret, timestamp + "." + rawBody))
// Callbacks outside the tolerance window are refused so a captured request can't be replayed
// later; replays inside the window are harmless because confirming a purchase is idempotent.
public final class WebhookSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    public WebhookSignature(String secret, Duration tolerance, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tolerance = tolerance;
        this.clock = clock;
    }

    public boolean isValid(String timestamp, String signature, byte[] body) {
        if (timestamp == null || signature == null) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        Duration age = Duration.between(Instant.ofEpochSecond(seconds), clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            return false;
        }
        byte[] expected = sign(timestamp.trim(), body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public String sign(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
