package com.uyir.hospital.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// hospital.payout.* in application.properties.
// provider: "http" onboards through payment-service (Razorpay Route); "mock" activates every payout
// account at once (local testing only). category / subcategory: the Razorpay business category
// every hospital is registered under.
@ConfigurationProperties("hospital.payout")
public record PayoutProperties(
        String provider,
        String baseUrl,
        String category,
        String subcategory,
        Duration connectTimeout,
        Duration readTimeout) {
}
