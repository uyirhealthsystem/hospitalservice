package com.uyir.hospital.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// hospital.internal.* in application.properties. apiKey is the shared secret other Uyir services send
// as X-Internal-Api-Key on service-to-service calls (POST /api/v1/hospital/internal/lookup).
@ConfigurationProperties("hospital.internal")
public record InternalApiProperties(String apiKey) {
}
