package com.uyir.hospital.payment;

import com.uyir.hospital.dto.PayoutAccountRequest;
import com.uyir.hospital.model.enums.PayoutAccountStatus;
import java.time.Instant;
import java.util.List;

// payment-service's linked accounts, seen from this service: "pay this hospital into this bank
// account". Implementations: HttpLinkedAccountClient (real) and MockLinkedAccountClient (local testing).
public interface LinkedAccountClient {

    // The ownerType this service onboards in payment-service (its payment.linked-accounts.owner-roles[HOSPITAL]).
    String OWNER_TYPE = "HOSPITAL";

    record LinkedAccount(
            String id,
            PayoutAccountStatus status,
            String providerAccountId,
            List<String> requirements,
            String legalBusinessName,
            String bankAccountLast4,
            String ifscCode,
            String beneficiaryName,
            Instant activatedAt) {
    }

    // Creates the hospital's linked account, or continues / updates it: payment-service skips the
    // steps already done at Razorpay. The bearer token is the hospital user's; payment-service keys
    // the account to that user, so a hospital can only ever set its own bank account.
    LinkedAccount onboard(String hospitalId, PayoutAccountRequest request, String bearerToken);

    // Asks payment-service (which asks Razorpay) where the activation stands now.
    LinkedAccount refresh(String linkedAccountId, String bearerToken);
}
