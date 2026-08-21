package com.uyir.hospital.model.embedded;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Operations {

    private boolean hospitalInsured;
    private boolean dedicatedPharmacyAvailable;
    private boolean hospitalManagementSoftwareInUse;
    private List<String> empanelledInsuranceCompanies;
    private ScansAvailable scansAvailable;
}
