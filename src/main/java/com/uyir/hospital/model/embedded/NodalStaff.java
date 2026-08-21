package com.uyir.hospital.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodalStaff {

    private String name;
    private String mobileNumber;
    private String designation;
}
