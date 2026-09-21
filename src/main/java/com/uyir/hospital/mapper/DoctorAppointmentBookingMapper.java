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
                .hospitalId(booking.getHospitalId())
                .doctorId(booking.getDoctorId())
                .appointmentDateTime(booking.getAppointmentDateTime())
                .reason(booking.getReason())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
