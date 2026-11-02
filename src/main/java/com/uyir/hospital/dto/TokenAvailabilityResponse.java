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
public class TokenAvailabilityResponse {

    private String hospitalId;
    private String doctorId;
    private LocalDate date;
    // null = the hospital has set no limit for this doctor
    private Integer tokenLimit;
    private int bookedTokens;
    // null when tokenLimit is null
    private Integer remainingTokens;
    private boolean available;
}
