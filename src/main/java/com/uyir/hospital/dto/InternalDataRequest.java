package com.uyir.hospital.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// At least one of hospitalId / doctorId. An empty include returns HOSPITAL and/or DOCTOR for the ids given,
// plus DOCTOR_STATUS when both are given. hospitalFields / doctorFields pick the fields returned (dotted
// paths reach nested ones, e.g. "contactDetails.phone"); left empty, the whole object comes back.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalDataRequest {

    private String hospitalId;
    private String doctorId;
    private List<InternalDataSection> include;
    private List<String> hospitalFields;
    private List<String> doctorFields;
}
