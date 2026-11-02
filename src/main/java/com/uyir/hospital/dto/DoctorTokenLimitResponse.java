package com.uyir.hospital.dto;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorTokenLimitResponse {

    private String hospitalId;
    private String doctorId;
    // null = unlimited
    private Integer dailyLimit;
    // yyyy-MM-dd -> limit for that date (today and later only)
    private Map<String, Integer> dateLimits;
    private Instant updatedAt;
}
