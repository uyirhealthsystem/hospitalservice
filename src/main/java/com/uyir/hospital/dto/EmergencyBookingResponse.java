package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.ArrivalMode;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyBookingResponse {

    private String id;
    private String patientId;
    private String patientName;
    private String patientPhone;
    private String hospitalId;
    private String emergencyType;
    private EmergencyBookingStatus status;
    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant rejectedAt;
    private String rejectionReason;
    private Instant cancelledAt;
    private String cancellationReason;
    private Instant completedAt;
    // The ambulance service ride bringing the patient here, set by the patient (PATCH {id}/ambulance).
    private String ambulanceBookingId;
    private Instant ambulanceLinkedAt;
    // How the patient is getting here: UYIR_AMBULANCE / OTHER_AMBULANCE / OWN_WAY (null until the patient says).
    private ArrivalMode arrivalMode;
    // When the patient's phone last reported its position (null if never). The position itself: GET {id}/patient-location.
    private Instant patientLocationUpdatedAt;
    private Instant updatedAt;
}
