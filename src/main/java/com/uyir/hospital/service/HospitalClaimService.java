package com.uyir.hospital.service;

import com.uyir.hospital.dto.ClaimHospitalRequest;
import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.security.Role;

// PRO onboarding and claiming (option B: hospital id + one-time claim code).
//
//   1. A PRO creates the hospital                -> onboard(): unowned hospital + claim code emailed
//   2. The hospital's HOSPITAL account logs in   -> claim(): id + code -> the account owns the hospital
//   3. Code lost / expired                       -> resendCode(): new code (old one stops working)
//
// The code is 8 characters from an unambiguous alphabet, stored only as a BCrypt hash, valid for 7 days,
// single use, and locked after 5 wrong attempts until a new one is issued.
public interface HospitalClaimService {

    HospitalResponse onboard(String proUserId, HospitalRequest request);

    HospitalResponse claim(String userId, ClaimHospitalRequest request);

    // The onboarding PRO, an ADMIN or a SUPER_ADMIN may resend.
    HospitalResponse resendCode(String callerUserId, Role callerRole, String hospitalId);
}
