package com.uyir.hospital.model.enums;

// Razorpay Route activation of a hospital's payout account, as payment-service reports it.
// Patients' payments can be split to the hospital only once it is ACTIVATED.
public enum PayoutAccountStatus {
    PENDING_DETAILS,
    UNDER_REVIEW,
    NEEDS_CLARIFICATION,
    ACTIVATED,
    SUSPENDED
}
