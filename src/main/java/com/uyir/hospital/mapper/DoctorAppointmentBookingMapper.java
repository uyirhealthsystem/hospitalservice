package com.uyir.hospital.mapper;

import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.FollowUpResponse;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.embedded.FollowUpInstruction;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class DoctorAppointmentBookingMapper {

    public DoctorAppointmentBookingResponse toResponse(DoctorAppointmentBooking booking) {
        return DoctorAppointmentBookingResponse.builder()
                .id(booking.getId())
                .patientId(booking.getPatientId())
                .patientName(booking.getPatientName())
                .patientPhone(booking.getPatientPhone())
                .patientAge(booking.getPatientAge())
                .patientGender(booking.getPatientGender())
                .bookedFor(booking.getBookedFor())
                .relationship(booking.getRelationship())
                .hospitalId(booking.getHospitalId())
                .doctorId(booking.getDoctorId())
                .appointmentDateTime(booking.getAppointmentDateTime())
                .durationMinutes(booking.getDurationMinutes())
                .tokenNumber(booking.getTokenNumber())
                .consultationType(booking.getConsultationType())
                .visitType(booking.getVisitType())
                .previousAppointmentId(booking.getPreviousAppointmentId())
                .reason(booking.getReason())
                .symptoms(booking.getSymptoms())
                .status(booking.getStatus())
                .cancellationReason(booking.getCancellationReason())
                .cancelledBy(booking.getCancelledBy())
                .cancelledAt(booking.getCancelledAt())
                .patientEmail(booking.getPatientEmail())
                .followUp(toFollowUpResponse(booking.getFollowUp()))
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    private static FollowUpResponse toFollowUpResponse(FollowUpInstruction followUp) {
        if (followUp == null || followUp.getDueDate() == null) {
            return null;
        }
        LocalDate due = LocalDate.parse(followUp.getDueDate());
        return FollowUpResponse.builder()
                .dueDate(due)
                .instructions(followUp.getInstructions())
                .bookableFrom(FollowUpInstruction.bookableFrom(due))
                .bookableUntil(FollowUpInstruction.bookableUntil(due))
                .followUpBookingId(followUp.getFollowUpBookingId())
                .setAt(followUp.getSetAt())
                .build();
    }
}
