package com.uyir.hospital.dto;

import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.embedded.Facilities;
import com.uyir.hospital.model.embedded.Operations;
import com.uyir.hospital.model.embedded.StaffDetails;
import com.uyir.hospital.model.embedded.SurgicalNetwork;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalResponse {

    private String id;
    private String userId;
    private String hospitalName;
    private String registrationNumber;
    private OwnershipType ownershipType;
    private HospitalType hospitalType;
    private Address address;
    private ContactDetails contactDetails;
    private Facilities facilities;
    private EmergencyServices emergencyServices;
    private StaffDetails staffDetails;
    private Operations operations;
    private SurgicalNetwork surgicalNetwork;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    // PRO onboarding / claiming. The claim code itself is never returned - only where it was sent.
    private String onboardedByProUserId;
    // true once a HOSPITAL account owns this hospital (userId set).
    private boolean claimed;
    // Masked contactDetails.email the current claim code was sent to (e.g. "ad***@hospital.in"); null when
    // claimed or no code is active.
    private String claimCodeSentTo;
    private Instant claimCodeExpiresAt;

    // true once the hospital has set its analytics PIN. The PIN (hash) itself is never returned.
    private boolean analyticsPinSet;
}
