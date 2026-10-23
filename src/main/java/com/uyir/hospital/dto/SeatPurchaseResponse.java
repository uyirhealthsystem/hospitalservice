package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.SeatPurchaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatPurchaseResponse {

    private String id;
    private String hospitalId;
    private int quantity;
    private BigDecimal pricePerSeat;
    private BigDecimal amount;
    private String currency;
    private SeatPurchaseStatus status;
    private String paymentId;
    // Open this to pay while status is PENDING; null once PAID.
    private String checkoutUrl;
    private String failureReason;
    private Instant createdAt;
    private Instant paidAt;
}
