package com.uyir.hospital.model;

import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "emergency_bookings")
public class EmergencyBooking {

    @Id
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
