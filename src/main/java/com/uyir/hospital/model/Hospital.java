package com.uyir.hospital.model;

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
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hospitals")
public class Hospital {

    @Id
    private String id;

    // AuthService user id (X-User-Id) of the HOSPITAL account that owns this hospital.
    // One hospital per user - enforced in HospitalServiceImpl.create and by a unique index in MongoIndexConfig.
    // Null for a hospital a PRO onboarded until its HOSPITAL account claims it (HospitalClaimService).
    private String userId;

    // AuthService user id of the PRO who onboarded this hospital (null when the hospital registered itself).
    private String onboardedByProUserId;

    // Claim code for a PRO-onboarded hospital: emailed to contactDetails.email, stored only as a BCrypt
    // hash, single use. Cleared once the hospital is claimed.
    private String claimCodeHash;
    private Instant claimCodeIssuedAt;
    private Instant claimCodeExpiresAt;
    // Wrong codes entered against the current claim code; claiming locks at the limit until a resend.
    private int claimAttempts;
    private Instant claimedAt;

    // Analytics PIN (AnalyticsPinService): the owning hospital account must enter it every time it opens its
    // own analytics/metrics. Stored only as a BCrypt hash; null = not set yet, analytics locked.
    private String analyticsPinHash;
    private Instant analyticsPinUpdatedAt;
    // Wrong PINs in a row; at the limit the PIN locks until analyticsPinLockedUntil and the count restarts.
    private int analyticsPinFailedAttempts;
    private Instant analyticsPinLockedUntil;
    // Forgotten-PIN reset code, emailed to contactDetails.email. Hash only, single use, short-lived.
    private String analyticsPinResetCodeHash;
    private Instant analyticsPinResetCodeIssuedAt;
    private Instant analyticsPinResetCodeExpiresAt;
    private int analyticsPinResetAttempts;

    private String hospitalName;

    @Indexed(unique = true)
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
}
