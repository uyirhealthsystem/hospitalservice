package com.uyir.hospital.payment;

import com.uyir.hospital.model.CommissionInvoice;
import com.uyir.hospital.model.SeatPurchase;

// payment-service, seen from this service: "purchase X owes Y, where can the hospital pay?".
// Implementations: HttpPaymentClient (real) and MockPaymentClient (local testing).
public interface PaymentClient {

    // This service's referenceTypes in payment-service (its payment.consumers[...] entries).
    String REFERENCE_TYPE = "HOSPITAL_SEAT";
    String INVOICE_REFERENCE_TYPE = "HOSPITAL_INVOICE";

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

    // Same, for a hospital's monthly commission invoice (Idempotency-Key per invoice).
    Intent createInvoiceIntent(CommissionInvoice invoice, String payerId, String bearerToken);
}
