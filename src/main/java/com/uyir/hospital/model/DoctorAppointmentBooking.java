package com.uyir.hospital.model;

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
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "doctor_appointment_bookings")
public class DoctorAppointmentBooking {

    // Bookings created before durationMinutes existed have no stored value; treat them as this long.
    public static final int DEFAULT_DURATION_MINUTES = 15;

    @Id
    private String id;

    // patientId is always the account holder who made the booking; the patient* fields below
    // describe the person being seen, which differs when bookedFor is FAMILY_MEMBER.
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

    public int effectiveDurationMinutes() {
        return durationMinutes != null ? durationMinutes : DEFAULT_DURATION_MINUTES;
    }

    public Instant appointmentEndTime() {
        return appointmentDateTime.plusSeconds(effectiveDurationMinutes() * 60L);
    }
}
