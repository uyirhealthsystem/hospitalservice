package com.uyir.hospital.model;

import com.uyir.hospital.model.enums.AppointmentStatus;
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
@Document(collection = "doctor_appointment_bookings")
public class DoctorAppointmentBooking {

    @Id
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
