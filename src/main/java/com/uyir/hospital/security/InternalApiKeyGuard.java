package com.uyir.hospital.security;

import com.uyir.hospital.exception.InternalAuthException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

// Service-to-service calls carry no user token (no X-User-Id / X-User-Role); they are authenticated by a
// shared key in the X-Internal-Api-Key header. With no key configured every internal call is refused.
@Component
public class InternalApiKeyGuard {

    public static final String HEADER = "X-Internal-Api-Key";

    private final byte[] expected;

    public InternalApiKeyGuard(InternalApiProperties props) {
        String key = props.apiKey();
        this.expected = key == null || key.isBlank() ? null : key.trim().getBytes(StandardCharsets.UTF_8);
    }

    public void check(String presented) {
        if (expected == null) {
            throw new InternalAuthException("Internal API is not configured (hospital.internal.api-key)");
        }
        if (presented == null || presented.isBlank()
                || !MessageDigest.isEqual(expected, presented.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new InternalAuthException("Missing or invalid " + HEADER + " header");
        }
    }
}
