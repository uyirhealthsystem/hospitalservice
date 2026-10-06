package com.uyir.hospital.mapper;

import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.model.DoctorAppointmentBooking;
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
                .consultationType(booking.getConsultationType())
                .visitType(booking.getVisitType())
                .previousAppointmentId(booking.getPreviousAppointmentId())
                .reason(booking.getReason())
                .symptoms(booking.getSymptoms())
                .status(booking.getStatus())
                .cancellationReason(booking.getCancellationReason())
                .cancelledBy(booking.getCancelledBy())
                .cancelledAt(booking.getCancelledAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
