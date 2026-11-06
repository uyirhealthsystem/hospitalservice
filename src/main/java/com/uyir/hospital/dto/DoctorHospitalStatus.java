package com.uyir.hospital.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// The doctor's standing at one hospital. bookable = both active and the doctor holds a seat there
// (only seated doctors take appointments or emergencies).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorHospitalStatus {

    private boolean hospitalActive;
    private boolean doctorActive;
    private boolean seated;
    private boolean associated;
    private boolean associationActive;
    private String department;
    // The hospital-specific fee from the association, else the doctor's own fee
    private BigDecimal consultationFee;
    private boolean checkedInHere;
    private String currentHospitalId;
    private Instant checkedInAt;
    private boolean bookable;
}
