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

    // Populated only when handlesEmergencies is true. Official names from the 16 SOS emergency conditions
    // (EmergencyConditionCatalog), enforced on save. A doctor on duty here must handle the condition too.
    private List<String> specialtyEmergencyConditionsHandled;

    private boolean ambulanceAvailable;
    private List<AmbulanceType> ambulanceTypes;
    private boolean ambulance24x7Available;
    private boolean willingToAttachAmbulanceToUdhs;

    public boolean handlesEmergencyType(String emergencyType) {
        return handlesEmergencies
                && specialtyEmergencyConditionsHandled != null
                && specialtyEmergencyConditionsHandled.stream().anyMatch(condition -> condition.equalsIgnoreCase(emergencyType));
    }
}
