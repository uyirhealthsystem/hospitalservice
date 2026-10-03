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
public class HospitalMatricsRequest {

    // Both optional, yyyy-MM-dd, inclusive (IST); defaults to the last 30 days.
    // Only the booking activity counts are windowed - hospital/doctor counts are always "now".
    private LocalDate fromDate;
    private LocalDate toDate;
}
