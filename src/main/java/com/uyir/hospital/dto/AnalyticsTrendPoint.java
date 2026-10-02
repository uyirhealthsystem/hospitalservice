package com.uyir.hospital.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTrendPoint {

    private LocalDate date;
    private long emergencyBookings;
    private long appointments;
}
