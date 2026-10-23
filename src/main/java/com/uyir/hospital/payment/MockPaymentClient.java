package com.uyir.hospital.payment;

import com.uyir.hospital.model.SeatPurchase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// LOCAL TESTING ONLY (hospital.seat.payment.provider=mock): every seat purchase is paid immediately.
@Component
@ConditionalOnProperty(name = "hospital.seat.payment.provider", havingValue = "mock")
public class MockPaymentClient implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentClient.class);

    public MockPaymentClient() {
        log.warn("Using the MOCK payment provider for seats: payments are not real. Never enable this in production.");
    }

    @Override
    public Intent createIntent(SeatPurchase purchase, String payerId, String bearerToken) {
        return new Intent("mock-" + purchase.getId(), PaymentStatus.PAID, null);
    }
}
