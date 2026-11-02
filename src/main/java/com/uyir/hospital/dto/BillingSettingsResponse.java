package com.uyir.hospital.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSettingsResponse {

    private BigDecimal commissionPerAppointment;
    private String currency;
    private int dueDays;
    // null while the configured default is in use
    private Instant updatedAt;
    private String updatedBy;
}
