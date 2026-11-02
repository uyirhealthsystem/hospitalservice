package com.uyir.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientActivitySummary {

    private long totalEmergencies;
    private long activeEmergencies;
    private long completedEmergencies;
    private long totalAppointments;
    private long upcomingAppointments;
    private long completedAppointments;
    private long cancelledAppointments;
    private long missedAppointments;
}
