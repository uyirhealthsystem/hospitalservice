package com.uyir.hospital.mapper;

import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.model.EmergencyBooking;
import org.springframework.stereotype.Component;

@Component
public class EmergencyBookingMapper {

    public EmergencyBookingResponse toResponse(EmergencyBooking booking) {
        return EmergencyBookingResponse.builder()
                .id(booking.getId())
                .patientId(booking.getPatientId())
                .patientName(booking.getPatientName())
                .patientPhone(booking.getPatientPhone())
                .hospitalId(booking.getHospitalId())
                .emergencyType(booking.getEmergencyType())
                .status(booking.getStatus())
                .requestedAt(booking.getRequestedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
