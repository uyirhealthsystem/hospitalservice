package com.uyir.hospital.catalog;

// One of the 16 emergency conditions a patient picks from in SOS (src/main/resources/emergency-conditions.json).
// Used for emergency booking only - unrelated to the specialty / department list, which is for appointments.
public record EmergencyCondition(String code, String name, String icon) {
}
