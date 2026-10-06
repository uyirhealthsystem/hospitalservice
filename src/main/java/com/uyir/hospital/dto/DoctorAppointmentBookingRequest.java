package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.BookedFor;
import com.uyir.hospital.model.enums.ConsultationType;
import com.uyir.hospital.model.enums.PatientRelationship;
import com.uyir.hospital.model.enums.Sex;
import com.uyir.hospital.model.enums.VisitType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class DoctorAppointmentBookingRequest {

    @NotBlank
    private String hospitalId;

    @NotBlank
    private String doctorId;

    @NotNull
    @Future
    private Instant appointmentDateTime;

    // Defaults to DoctorAppointmentBooking.DEFAULT_DURATION_MINUTES when omitted.
    @Min(5)
    @Max(120)
    private Integer durationMinutes;

    // Defaults to IN_PERSON when omitted.
    private ConsultationType consultationType;

    // Defaults to NEW when omitted; FOLLOW_UP requires previousAppointmentId.
    private VisitType visitType;

    private String previousAppointmentId;

    private String reason;

    @Size(max = 20)
    private List<@NotBlank @Size(max = 100) String> symptoms;

    @NotBlank
    private String patientName;

    @NotBlank
    private String patientPhone;

    @NotNull
    @Min(0)
    @Max(130)
    private Integer patientAge;

    @NotNull
    private Sex patientGender;

    // Defaults to SELF when omitted; FAMILY_MEMBER requires relationship.
    private BookedFor bookedFor;

    private PatientRelationship relationship;
}
