package com.uyir.hospital.security;

// Must match the role names authservice puts in the token (forwarded as X-User-Role).
public enum Role {
    PATIENT,
    HOSPITAL,
    ADMIN,
    SUPER_ADMIN
}
