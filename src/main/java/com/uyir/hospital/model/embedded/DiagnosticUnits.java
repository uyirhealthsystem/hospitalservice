package com.uyir.hospital.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticUnits {

    private boolean lab;
    private boolean scanCentre;
    private boolean pathology;
}
