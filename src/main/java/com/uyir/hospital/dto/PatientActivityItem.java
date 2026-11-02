package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.PatientActivityType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// One entry in the patient's timeline. The flat fields are common to both kinds so a client can render the
// list without branching; exactly one of emergencyBooking / appointment carries the full booking.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientActivityItem {

    private PatientActivityType type;
    private String id;
    private String hospitalId;
    private String hospitalName;
    // Appointments only - an emergency is booked against the hospital, not a doctor.
    private String doctorId;
    private String doctorName;
    // Emergency condition name, or the appointment's reason.
    private String title;
    // EmergencyBookingStatus or AppointmentStatus name.
    private String status;
    // The person seen - differs from the account holder when an appointment is booked for a family member.
    private String patientName;
    // requestedAt for an emergency, appointmentDateTime for an appointment.
    private Instant activityAt;
    // Emergency still REQUESTED / ACCEPTED, or appointment CONFIRMED / RESCHEDULED and not yet started.
    private boolean active;
    private EmergencyBookingResponse emergencyBooking;
    private DoctorAppointmentBookingResponse appointment;
}
