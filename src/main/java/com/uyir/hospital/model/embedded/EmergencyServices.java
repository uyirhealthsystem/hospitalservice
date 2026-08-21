package com.uyir.hospital.model.embedded;

import com.uyir.hospital.model.enums.AmbulanceType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyServices {

    private boolean handlesEmergencies;

    // Populated only when handlesEmergencies is true; expected to match the notified list of 13 specialty emergency conditions
    private List<String> specialtyEmergencyConditionsHandled;

    private boolean ambulanceAvailable;
    private List<AmbulanceType> ambulanceTypes;
    private boolean ambulance24x7Available;
    private boolean willingToAttachAmbulanceToUdhs;
}
