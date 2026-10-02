package com.uyir.hospital.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSnapshotRequest {

    @Size(max = 120)
    private String label;

    // Both optional; same defaults as the live summary endpoint (last 30 days)
    private LocalDate fromDate;
    private LocalDate toDate;
}
