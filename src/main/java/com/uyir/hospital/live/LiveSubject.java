package com.uyir.hospital.live;

import com.uyir.hospital.security.Role;

// Who a live connection belongs to: a hospital (by hospital id, not its user id) or a patient (by user id).
public record LiveSubject(Role role, String id) {

    public static final String ATTRIBUTE = "liveSubject";

    public static LiveSubject hospital(String hospitalId) {
        return new LiveSubject(Role.HOSPITAL, hospitalId);
    }

    public static LiveSubject patient(String patientId) {
        return new LiveSubject(Role.PATIENT, patientId);
    }

    String key() {
        return role + ":" + id;
    }
}
