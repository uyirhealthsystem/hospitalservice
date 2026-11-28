package com.uyir.hospital.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body of POST /analytics/my-hospital and POST /metrics/my-hospital (HOSPITAL only).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyHospitalAnalyticsRequest {

    // The hospital's analytics PIN - required on every call; there is no "unlocked" session.
    private String pin;

    // Both optional, yyyy-MM-dd, inclusive (IST); defaults to the last 30 days.
    private LocalDate fromDate;
    private LocalDate toDate;
}
