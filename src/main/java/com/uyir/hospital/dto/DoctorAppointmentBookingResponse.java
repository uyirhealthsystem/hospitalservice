package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.BookedFor;
import com.uyir.hospital.model.enums.CancelledBy;
import com.uyir.hospital.model.enums.ConsultationType;
import com.uyir.hospital.model.enums.PatientRelationship;
import com.uyir.hospital.model.enums.Sex;
import com.uyir.hospital.model.enums.VisitType;
import java.time.Instant;
import java.util.List;
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
    private Integer patientAge;
    private Sex patientGender;
    private BookedFor bookedFor;
    private PatientRelationship relationship;
    private String hospitalId;
    private String doctorId;
    private Instant appointmentDateTime;
    private Integer durationMinutes;
    private ConsultationType consultationType;
    private VisitType visitType;
    private String previousAppointmentId;
    private String reason;
    private List<String> symptoms;
    private AppointmentStatus status;
    private String cancellationReason;
    private CancelledBy cancelledBy;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;
}
