package com.uyir.hospital.dto;

// What a calling service can ask POST /api/v1/hospital/internal/lookup for.
public enum InternalDataSection {
    // Hospital profile (needs hospitalId; narrowed by hospitalFields)
    HOSPITAL,
    // Doctor profile (needs doctorId; narrowed by doctorFields)
    DOCTOR,
    // How the doctor relates to the hospital: seated, associated, checked in, bookable (needs both ids)
    DOCTOR_STATUS,
    // Doctors sitting in the hospital's seats - the only ones bookable there (needs hospitalId; doctorFields)
    SEATED_DOCTORS,
    // Doctors checked in at the hospital right now (needs hospitalId; doctorFields)
    ON_DUTY_DOCTORS,
    // The hospital's seat summary (needs hospitalId)
    SEATS
}
