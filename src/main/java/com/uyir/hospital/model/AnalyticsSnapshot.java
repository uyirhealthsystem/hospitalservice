package com.uyir.hospital.model;

import com.uyir.hospital.dto.DistrictAnalyticsSummary;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// A frozen copy of a district summary. Live analytics are recomputed from current data on every
// request, so bed capacity, hospital status, etc. as they stood on a past date can't be
// reconstructed later - saving a snapshot is how a district admin keeps that record.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hospital_analytics_snapshots")
public class AnalyticsSnapshot {

    @Id
    private String id;

    private String district;
    private String label;

    private LocalDate fromDate;
    private LocalDate toDate;

    private DistrictAnalyticsSummary summary;

    private String createdBy;
    private Instant createdAt;
}
