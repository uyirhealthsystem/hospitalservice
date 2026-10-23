package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.PayoutAccountStatus;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutAccountResponse {

    private String hospitalId;
    private PayoutAccountStatus status;
    // true once Razorpay activated the account: patients' payments can then be paid out to it.
    private boolean payoutsEnabled;
    // What Razorpay still needs while NEEDS_CLARIFICATION; fix these and PUT the details again.
    private List<String> requirements;
    private String legalBusinessName;
    // Only the last 4 digits are ever returned.
    private String bankAccountLast4;
    private String ifscCode;
    private String beneficiaryName;
    private Instant activatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
