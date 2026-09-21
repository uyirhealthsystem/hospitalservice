package com.uyir.hospital.dto;

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
    private Instant updatedAt;
}
