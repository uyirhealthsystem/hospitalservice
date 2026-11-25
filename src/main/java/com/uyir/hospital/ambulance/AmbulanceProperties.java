package com.uyir.hospital.ambulance;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// hospital.ambulance.* in application.properties: the ambulance service, read to show a hospital the
// ambulance bringing an emergency patient to it.
// internalKey must equal the ambulance service's AMBULANCE_INTERNAL_KEY (sent as X-Internal-Key).
// Empty = the incoming-ambulance view answers 503, the rest of the service is unaffected.
@ConfigurationProperties("hospital.ambulance")
public record AmbulanceProperties(
        String baseUrl,
        String internalKey,
        Duration connectTimeout,
        Duration readTimeout) {
}
