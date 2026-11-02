package com.uyir.hospital.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyTokenLimitRequest {

    // Tokens issued for this doctor every day. null removes the limit (unlimited bookings).
    @Min(1)
    @Max(1000)
    private Integer dailyLimit;
}
