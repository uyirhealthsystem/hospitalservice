package com.uyir.hospital.model.enums;

// Stored: ISSUED or PAID. OVERDUE is never stored; responses show it for an ISSUED invoice past its due date.
public enum InvoiceStatus {
    ISSUED,
    OVERDUE,
    PAID
}
