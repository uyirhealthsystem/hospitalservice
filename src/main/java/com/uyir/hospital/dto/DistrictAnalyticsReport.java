package com.uyir.hospital.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Everything the district analytics endpoints return, computed from a single data load.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictAnalyticsReport {

    private DistrictAnalyticsSummary summary;
    private List<HospitalAnalyticsResponse> hospitals;
    private List<AnalyticsTrendPoint> trends;
}
