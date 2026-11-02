package com.uyir.hospital.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// When the patient should come back: exactly one of afterDays (counted from the visit date) or date.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpRequest {

    @Min(1)
    @Max(365)
    private Integer afterDays;

    // yyyy-MM-dd
    private LocalDate date;

    // e.g. "Review with fasting blood sugar report"
    @Size(max = 1000)
    private String instructions;
}
