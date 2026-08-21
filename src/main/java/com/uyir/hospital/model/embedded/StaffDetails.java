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
public class StaffDetails {

    private Integer nursingStaffCount;
    private Integer supportiveStaffCount;

    // At least 2 dedicated UDHS staff required; no upper bound
    private List<NodalStaff> udhsStaff;
}
