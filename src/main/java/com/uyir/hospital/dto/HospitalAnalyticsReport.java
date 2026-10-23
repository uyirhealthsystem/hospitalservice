package com.uyir.hospital.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// A single hospital's own analytics (POST /api/v1/hospital/analytics/my-hospital): its figures plus
// daily trends, computed from one data load.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalAnalyticsReport {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Instant generatedAt;

    private HospitalAnalyticsResponse hospital;
    private List<AnalyticsTrendPoint> trends;
}
