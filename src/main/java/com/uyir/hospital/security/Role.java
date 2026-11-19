package com.uyir.hospital.security;

// Must match the role names authservice puts in the token (forwarded as X-User-Role).
public enum Role {
    PATIENT,
    HOSPITAL,
    // Field officer who onboards hospitals (pro service). May create a hospital on its behalf - the hospital
    // is then unowned until its own HOSPITAL account claims it with the emailed claim code.
    PRO,
    ADMIN,
    SUPER_ADMIN
}
