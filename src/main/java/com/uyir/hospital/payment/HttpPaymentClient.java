package com.uyir.hospital.payment;

import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.PaymentUnavailableException;
import com.uyir.hospital.model.SeatPurchase;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// POST {baseUrl}/api/payments/intents
//   Authorization: Bearer <the hospital user's JWT>   (payment-service checks payerId == token sub)
//   Idempotency-Key: hospital-seat-purchase-<purchaseId>
//   { "referenceType": "HOSPITAL_SEAT", "referenceId": "<purchaseId>", "payerId": "...", "amount": 20000.00, "currency": "INR" }
// A network failure is retried once; the idempotency key makes that safe.
@Component
@ConditionalOnProperty(name = "hospital.seat.payment.provider", havingValue = "http", matchIfMissing = true)
public class HttpPaymentClient implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPaymentClient.class);
    private static final int ATTEMPTS = 2;

    private record IntentRequest(
            String referenceType, String referenceId, String payerId, BigDecimal amount, String currency, String description) {
    }

    private final RestClient http;

    public HttpPaymentClient(SeatProperties props) {
        SeatProperties.Payment cfg = props.payment();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.connectTimeout());
        factory.setReadTimeout(cfg.readTimeout());
        this.http = RestClient.builder().requestFactory(factory).baseUrl(cfg.baseUrl()).build();
    }

    @Override
    public Intent createIntent(SeatPurchase purchase, String payerId, String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new ForbiddenException("An Authorization bearer token is required to pay for seats");
        }
        IntentRequest body = new IntentRequest(
                REFERENCE_TYPE,
                purchase.getId(),
                payerId,
                purchase.getAmount(),
                purchase.getCurrency(),
                purchase.getQuantity() + " doctor seat(s)");

        for (int attempt = 1; ; attempt++) {
            try {
                Intent intent = http.post()
                        .uri("/api/payments/intents")
                        .header("Authorization", "Bearer " + bearerToken)
                        .header("Idempotency-Key", "hospital-seat-purchase-" + purchase.getId())
                        .body(body)
                        .retrieve()
                        .body(Intent.class);
                if (intent == null || intent.id() == null || intent.status() == null) {
                    throw new RestClientException("incomplete response from payment-service");
                }
                return intent;
            } catch (ResourceAccessException e) {
                if (attempt < ATTEMPTS) {
                    log.warn("payment-service unreachable for seat purchase {} (attempt {}), retrying: {}",
                            purchase.getId(), attempt, e.getMessage());
                    continue;
                }
                throw unavailable(purchase, e);
            } catch (RestClientException e) {
                throw unavailable(purchase, e);
            }
        }
    }

    private static PaymentUnavailableException unavailable(SeatPurchase purchase, Exception e) {
        log.error("payment-service call failed for seat purchase {}: {}", purchase.getId(), e.getMessage());
        return new PaymentUnavailableException("Payment service unavailable, please retry");
    }
}
