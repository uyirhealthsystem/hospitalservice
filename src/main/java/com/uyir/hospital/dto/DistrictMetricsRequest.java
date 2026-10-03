package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictMetricsRequest {

    // Passed by the Admin service - the district this admin manages
    @NotBlank
    private String district;

    // Both optional, yyyy-MM-dd, inclusive (IST); defaults to the last 30 days
    private LocalDate fromDate;
    private LocalDate toDate;
}
