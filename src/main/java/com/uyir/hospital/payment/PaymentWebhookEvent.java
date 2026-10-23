package com.uyir.hospital.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.uyir.hospital.payment.PaymentClient.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

// Callback body from payment-service. referenceId is the SeatPurchase id; payerId, refundedAmount,
// occurredAt and the deprecated bookingId are also sent but not needed here.
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentWebhookEvent(
        @NotBlank String paymentId,
        @NotBlank String referenceType,
        @NotBlank String referenceId,
        @NotNull PaymentStatus status,
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotBlank String currency,
        String failureReason) {
}
