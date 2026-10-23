package com.uyir.hospital.model.enums;

// REQUESTED --accept--> ACCEPTED --complete--> COMPLETED   (hospital)
// REQUESTED --reject--> REJECTED                             (hospital)
// REQUESTED / ACCEPTED --cancel--> CANCELLED                 (patient)
// REJECTED, CANCELLED and COMPLETED are final.
public enum EmergencyBookingStatus {
    REQUESTED,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
