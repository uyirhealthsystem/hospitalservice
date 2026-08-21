package com.uyir.hospital.model.embedded;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalAssociation {

    private String hospitalId;
    private String department;
    private BigDecimal consultationFee;
    private boolean active;
}
