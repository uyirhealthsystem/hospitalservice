package com.uyir.hospital.dto;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSnapshotResponse {

    private String id;
    private String district;
    private String label;
    private LocalDate fromDate;
    private LocalDate toDate;
    private DistrictAnalyticsSummary summary;
    private String createdBy;
    private Instant createdAt;
}
