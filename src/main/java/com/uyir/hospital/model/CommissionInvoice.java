package com.uyir.hospital.model;

import com.uyir.hospital.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One hospital's Uyir commission for one calendar month (IST): every doctor appointment scheduled in
// that month that was not cancelled, each at the commission rate stamped on it when it was booked.
// Emergency bookings are never billed. Stored status is ISSUED or PAID; OVERDUE is ISSUED past dueDate.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "commission_invoices")
public class CommissionInvoice {

    // "<hospitalId>-<yyyy-MM>": one invoice per hospital per month, so re-running generation is harmless.
    @Id
    private String id;

    private String hospitalId;
    private String hospitalName;
    // yyyy-MM
    private String period;
    private Instant periodStart;
    private Instant periodEnd;

    private int appointmentCount;
    // Frozen at issue time, so later changes to those bookings don't change the bill.
    private List<String> appointmentIds;
    private BigDecimal amount;
    private String currency;

    private InvoiceStatus status;
    private Instant issuedAt;
    private Instant dueDate;

    private String paymentId;
    private String checkoutUrl;
    private String failureReason;
    private Instant paidAt;
    // Set when an admin records a payment made outside the app (bank transfer, cheque, ...).
    private String offlinePaymentReference;
    private String markedPaidBy;

    private Instant updatedAt;

    public static String idFor(String hospitalId, String period) {
        return hospitalId + "-" + period;
    }

    public boolean isOverdue(Instant now) {
        return status == InvoiceStatus.ISSUED && dueDate != null && dueDate.isBefore(now);
    }
}
