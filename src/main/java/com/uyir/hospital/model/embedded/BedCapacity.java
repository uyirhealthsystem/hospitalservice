package com.uyir.hospital.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedCapacity {

    private Integer general;
    private Integer icu;
    private Integer nicu;
}
