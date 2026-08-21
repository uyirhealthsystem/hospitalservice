package com.uyir.hospital.repository;

import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HospitalSearchCriteria {

    private String city;
    private String state;
    private HospitalType hospitalType;
    private OwnershipType ownershipType;
    private Boolean active;
}
