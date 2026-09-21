package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.AppointmentStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAppointmentBookingResponse {

    private String id;
    private String patientId;
    private String patientName;
    private String patientPhone;
    private String hospitalId;
    private String doctorId;
    private Instant appointmentDateTime;
    private String reason;
    private AppointmentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
