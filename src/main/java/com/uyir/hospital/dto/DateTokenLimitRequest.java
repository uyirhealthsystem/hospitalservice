package com.uyir.hospital.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateTokenLimitRequest {

    // Tokens issued for this doctor on one date, overriding the daily limit. 0 = no appointments that day.
    @NotNull
    @Min(0)
    @Max(1000)
    private Integer limit;
}
