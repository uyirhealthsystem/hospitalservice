package com.uyir.hospital.payment;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// hospital.seat.* in application.properties.
@ConfigurationProperties("hospital.seat")
public record SeatProperties(BigDecimal pricePerSeat, String currency, Payment payment) {

    // provider: "http" calls payment-service; "mock" marks every purchase PAID at once (local testing only).
    // webhookSecret must equal payment-service's payment.consumers[HOSPITAL_SEAT].webhook-secret.
    public record Payment(
            String provider,
            String baseUrl,
            String webhookSecret,
            Duration webhookTolerance,
            Duration connectTimeout,
            Duration readTimeout) {
    }
}
