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
public class HospitalAnalyticsRequest {

    @NotBlank
    private String district;

    @NotBlank
    private String hospitalId;

    private LocalDate fromDate;
    private LocalDate toDate;
}
