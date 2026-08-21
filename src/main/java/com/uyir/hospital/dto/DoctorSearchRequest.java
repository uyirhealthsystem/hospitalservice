package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.DoctorCategory;
import com.uyir.hospital.model.enums.EngagementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorSearchRequest {

    private String specialty;
    private String hospitalId;
    private EngagementType engagementType;
    private DoctorCategory doctorCategory;
    private Boolean active;
}
