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
public class ScansAvailable {

    private boolean usg;
    private boolean xray;
    private boolean ct;
    private boolean mri;
    private List<String> other;
}
