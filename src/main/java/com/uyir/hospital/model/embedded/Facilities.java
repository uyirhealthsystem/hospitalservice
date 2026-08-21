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
public class Facilities {

    private BedCapacity bedCapacity;
    private List<String> specialtyDepartments;
    private DiagnosticUnits diagnosticUnits;
    private LabDetails labDetails;
}
