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
public class LabDetails {

    private String labName;
    private String qualification;
    private List<String> facilities;
    private String scanCentreName;
    private String pcpndtRegistrationNumber;
}
