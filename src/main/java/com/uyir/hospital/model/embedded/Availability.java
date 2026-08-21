package com.uyir.hospital.model.embedded;

import com.uyir.hospital.model.enums.AvailabilityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Availability {

    private AvailabilityType type;

    // Free-text schedule, populated only when type is CUSTOM_TIMINGS
    private String customTimings;
}
