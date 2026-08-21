package com.uyir.hospital.repository;

import com.uyir.hospital.model.enums.DoctorCategory;
import com.uyir.hospital.model.enums.EngagementType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DoctorSearchCriteria {

    private String specialty;
    private String hospitalId;
    private EngagementType engagementType;
    private DoctorCategory doctorCategory;
    private Boolean active;
}
