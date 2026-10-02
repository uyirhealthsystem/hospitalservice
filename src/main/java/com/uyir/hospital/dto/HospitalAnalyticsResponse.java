package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.model.enums.HospitalType;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalAnalyticsResponse {

    private String hospitalId;
    private String hospitalName;
    private String city;
    private HospitalType hospitalType;
    private boolean active;
    private boolean handlesEmergencies;

    private long generalBeds;
    private long icuBeds;
    private long nicuBeds;

    private long associatedDoctors;
    private long checkedInDoctors;

    private long emergencyBookings;
    private Map<EmergencyBookingStatus, Long> emergencyBookingsByStatus;

    private long appointments;
    private Map<AppointmentStatus, Long> appointmentsByStatus;
}
