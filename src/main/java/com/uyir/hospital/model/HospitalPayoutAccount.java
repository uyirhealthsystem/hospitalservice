package com.uyir.hospital.model;

import com.uyir.hospital.model.enums.PayoutAccountStatus;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// The bank account a hospital is paid into: a Razorpay Route linked account, onboarded through
// payment-service. id = hospitalId (one per hospital). Only the last 4 digits of the bank account
// are kept; the full number goes to Razorpay through payment-service and is stored nowhere here.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hospital_payout_accounts")
public class HospitalPayoutAccount {

    @Id
    private String hospitalId;

    // payment-service's linked account id, and Razorpay's account id (acc_...) transfers go to.
    private String linkedAccountId;
    private String providerAccountId;

    private PayoutAccountStatus status;
    // What Razorpay still needs while NEEDS_CLARIFICATION, e.g. "settlements.ifsc_code (field_missing)".
    private List<String> requirements;

    private String legalBusinessName;
    private String bankAccountLast4;
    private String ifscCode;
    private String beneficiaryName;

    private Instant activatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
