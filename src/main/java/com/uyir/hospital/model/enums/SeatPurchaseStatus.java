package com.uyir.hospital.model.enums;

// Mirrors payment-service's PENDING | PAID | FAILED. A FAILED purchase can be paid again.
public enum SeatPurchaseStatus {
    PENDING,
    PAID,
    FAILED
}
