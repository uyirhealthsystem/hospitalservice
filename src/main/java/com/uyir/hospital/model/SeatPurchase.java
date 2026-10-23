package com.uyir.hospital.model;

import com.uyir.hospital.model.enums.SeatPurchaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// A hospital buying `quantity` extra seats in one payment. The seats are created only once
// payment-service reports the payment PAID (signed webhook).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "seat_purchases")
public class SeatPurchase {

    @Id
    private String id;

    private String hospitalId;
    private int quantity;
    private BigDecimal pricePerSeat;
    private BigDecimal amount;
    private String currency;

    private SeatPurchaseStatus status;
    private String paymentId;
    private String checkoutUrl;
    private String failureReason;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant paidAt;
}
