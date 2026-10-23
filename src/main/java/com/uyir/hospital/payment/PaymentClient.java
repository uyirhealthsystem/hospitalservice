package com.uyir.hospital.payment;

import com.uyir.hospital.model.SeatPurchase;

// payment-service, seen from this service: "purchase X owes Y, where can the hospital pay?".
// Implementations: HttpPaymentClient (real) and MockPaymentClient (local testing).
public interface PaymentClient {

    // This service's referenceType in payment-service (its payment.consumers[HOSPITAL_SEAT] entry).
    String REFERENCE_TYPE = "HOSPITAL_SEAT";

    enum PaymentStatus {
        PENDING,
        PAID,
        FAILED
    }

    record Intent(String id, PaymentStatus status, String checkoutUrl) {
    }

    // Creates the payment for the purchase, or returns the existing one: the Idempotency-Key is
    // per purchase, so retries and "pay again" after an expired checkout reuse the same payment.
    Intent createIntent(SeatPurchase purchase, String payerId, String bearerToken);
}
