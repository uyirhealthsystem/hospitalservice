package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.InvoiceStatus;
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
public class CommissionInvoiceResponse {

    private String id;
    private String hospitalId;
    private String hospitalName;
    private String period;
    private Instant periodStart;
    private Instant periodEnd;
    private int appointmentCount;
    private BigDecimal amount;
    private String currency;
    // ISSUED, OVERDUE (ISSUED past dueDate: new appointments blocked) or PAID
    private InvoiceStatus status;
    private Instant issuedAt;
    private Instant dueDate;
    private String paymentId;
    // Open this to pay while unpaid; null once PAID.
    private String checkoutUrl;
    private String failureReason;
    private Instant paidAt;
    private String offlinePaymentReference;
}
